package io.hyperfoil.tools.h5m.cli;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.hyperfoil.tools.h5m.api.*;
import io.hyperfoil.tools.h5m.svc.*;
import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.JsonComparison;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.readline.prompt.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@CommandDefinition(name="veritaserum",description = "find the truth", generateHelp = true)
public class Veritaserum implements Command<H5mCommandInvocation> {

    public static final int TEXT_LIMIT = 120;
    private static final Logger log = LoggerFactory.getLogger(Veritaserum.class);
    @Inject
    FolderService folderService;

    @Inject
    NodeService nodeService;

    @Inject
    ValueService valueService;

    @Inject
    NodeGroupService nodeGroupService;

    @Inject
    ProcessingService processingService;

    @Option(name = "username", description = "legacy db username", defaultValue = "quarkus", acceptNameWithoutDashes = true)
    String username;
    @Option(name = "password", description = "legacy db password", defaultValue = "quarkus", acceptNameWithoutDashes = true)
    String password;
    @Option(name = "url", description = "legacy connection url", defaultValue = "jdbc:postgresql://127.0.0.1:6000/horreum", acceptNameWithoutDashes = true)
    String url;
    @OptionList(name = "testId", description = "Horreum test ID")
    List<Long> testIds;
    @OptionList(name = "runId", description = "verify a specific run (optional)")
    List<Long> runIds;
    @Option(name = "limit", description = "max runs to verify", defaultValue = "5", acceptNameWithoutDashes = true)
    int limit;
    @Option(name = "offset", description = "max runs to verify", defaultValue = "0", acceptNameWithoutDashes = true)
    int offset;

    @Option(name = "pause", acceptNameWithoutDashes = true, description = "pause for user input after every batch", hasValue = false, defaultValue = "false")
    boolean pause;

    //controls for equality
    @Option(name = "ignore-nulls", description = "remove nulls from h5m arrays before comparing to Horreum",defaultValue = "false", acceptNameWithoutDashes = true)
    boolean ignoreNulls;

    @Option(name = "extractor-prefix", acceptNameWithoutDashes = true, description = "optional prefix for all extractor names", defaultValue = "")
    String extractorPrefix = ""; //setting value for @Inject testing in LoadLegacyTestsTest

    @Option(name = "combined-label-prefix", acceptNameWithoutDashes = true, description = "optional prefix for labels combined during schema merge",defaultValue = "")
    String combinedLabelPrefix = "";

    @Inject
    LoadLegacyTests loadLegacyTests;

    private JqValue purgeNulls(JqValue input){
        if(input == null || input.isNull()){
            return JqNull.NULL;
        }
        if(ignoreNulls){
            if(input.isObject()) {
                JqObject.Builder builder = JqObject.builder();
                JqObject obj = (JqObject) input;
                obj.forEach((k,v)->{
                    if(v!=null && !v.isNull()){
                        builder.put(k,v);
                    }
                });
                input = builder.build();
            }else if (input.isArray()){
                JqArray.ArrayBuilder builder = JqArray.arrayBuilder();
                JqArray ary = (JqArray)  input;
                ary.forEach(v->{
                    if(v!=null && !v.isNull()){
                        builder.add(v);
                    }
                });
                input = builder.build();
            }
        }
        return input;
    }



    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        CommandResult exitCode = CommandResult.SUCCESS;
        try {
            List<Delta> deltas = new ArrayList<>();
            Map<Long,Long> datasetToValueId = new HashMap<>();


            Map<String, String> props = new HashMap<>();
            props.put(AgroalPropertiesReader.MAX_SIZE, "1");
            props.put(AgroalPropertiesReader.MIN_SIZE, "1");
            props.put(AgroalPropertiesReader.INITIAL_SIZE, "1");
            props.put(AgroalPropertiesReader.MAX_LIFETIME_S, "57");
            props.put(AgroalPropertiesReader.ACQUISITION_TIMEOUT_S, "54");
            props.put(AgroalPropertiesReader.PRINCIPAL, username);
            props.put(AgroalPropertiesReader.CREDENTIAL, password);
            props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, "org.postgresql.Driver");
            props.put(AgroalPropertiesReader.JDBC_URL, url);
            AgroalDataSource legacyDs = AgroalDataSource.from(new AgroalPropertiesReader()
                    .readProperties(props).get());
            Folder folder = null;


            try (Connection legacyConn = legacyDs.getConnection()) {

                if (testIds == null || testIds.isEmpty()) {
                    //fetch all the tests
                    testIds = fetchTestIds(legacyConn);
                }
                //for each test
                for (Long testId : testIds) {


                    String testName = getTestName(legacyConn, testId);
                    if (testName == null) {
                        System.err.println("Test not found: " + testId);
                        exitCode = CommandResult.USAGE_ERROR;
                        continue;
                    }
                    invocation.println("Veritaserum "+testName+" id="+testId);
                    folder = folderService.find(testName);
                    boolean loadFolder = folder == null;
                    if (loadFolder) {
                        invocation.println("loading test " + testName + " id=" + testId);
                        loadLegacyTests.username = username;
                        loadLegacyTests.password = password;
                        loadLegacyTests.url = url;
                        loadLegacyTests.testId = testId;
                        loadLegacyTests.keepAll = true;
                        loadLegacyTests.combinedLabelPrefix=combinedLabelPrefix;
                        loadLegacyTests.extractorPrefix=extractorPrefix;
                        //int ec = loadLegacyTests.call();
                        CommandResult result = loadLegacyTests.execute(null);
                        if (result.getExitCode() != 0) {
                            invocation.println("Error loading test " + testName + " id=" + testId);
                            exitCode = CommandResult.FAILURE;
                            continue;
                        }
                        folder = folderService.find(testName);
                    }
                    if (folder == null) {
                        invocation.println("failed to find folder " + testName);
                        exitCode = CommandResult.USAGE_ERROR;
                        continue;
                    }
                    NodeGroup nodeGroup = nodeGroupService.byId(folder.groupId());

                    //for each run
                    if (runIds == null || runIds.isEmpty()) {
                        runIds = fetchRunIds(legacyConn, testId, limit, offset);
                    }
                    invocation.println(runIds.size() + " runs");
                    for (Long runId : runIds) {
                        JqValue runData = fetchRun(legacyConn, runId);


                        if (runData == null) {
                            invocation.println("failed to find run " + runId);
                            exitCode = CommandResult.USAGE_ERROR;
                            continue;
                        }
                        invocation.println("Uploading run " + runId);
                        long uploadId = valueService.createRootValue(folder.id(),runData);
                        boolean ok = processingService.awaitIngestion(uploadId,3,TimeUnit.MINUTES);
                        if (!ok) {
                            invocation.println("upload failed");
                            continue;
                        }

                        List<LoadLegacyTests.Transformer> transformers = loadTransformers(legacyConn, testId);
                        if (!transformers.isEmpty()) {
                            for (LoadLegacyTests.Transformer transformer : transformers) {
                                List<Node> transformerMatches = nodeService.findNodeByFqdn(transformer.name(), folder.groupId());
                                if (transformerMatches.isEmpty()) {
                                    String name = LoadLegacyTests.getRename(transformer, transformers.size());
                                    transformerMatches = nodeService.findNodeByFqdn(name, folder.groupId());
                                }
                                if (transformerMatches.isEmpty()) {
                                    invocation.println("failed to find match for transformer " + transformer.name());
                                    Delta missingTransformer = new Delta(
                                            DeltaType.MissingLabel,
                                            folder.name(),
                                            runId,
                                            -1,
                                            "transform",
                                            transformer.name(),
                                            transformer.id(),
                                            transformer.function(),
                                            JqObject.EMPTY,
                                            "-",
                                            -1L,
                                            "-",
                                            JqNull.NULL
                                    );
                                    deltas.add(missingTransformer);
                                    exitCode = CommandResult.FAILURE;
                                    continue;
                                }
                                Node transformerMatch = transformerMatches.get(0);
                                for (Node extractorNode : transformerMatch.sources()) {

                                    var matchingExtractor = transformer.extractors().stream().filter(e -> extractorNode.name().equals(loadLegacyTests.getExtractorRename( e.name() )) || extractorNode.name().equals(LoadLegacyTests.DEFAULT_PREFIX+loadLegacyTests.getExtractorRename( e.name() ))).findFirst().orElse(null);
                                    if (matchingExtractor == null) {
                                        invocation.println("failed to find match for transformer extractor " + extractorNode.name());
                                        exitCode = CommandResult.FAILURE;
                                        continue;
                                    }
                                    JqValue fromExtractor = extractRun(legacyConn, runId, matchingExtractor.jsonpath(), matchingExtractor.isArray());
                                    List<Value> h5mValues = valueService.getDescendantValues(uploadId, List.of(extractorNode.id()));
                                    JqValue fromH5m = h5mValues.isEmpty() ? null : purgeNulls(h5mValues.getFirst().data());
                                    boolean eq = equalish(fromExtractor, fromH5m);
                                    if (!eq) {
                                        Delta d = new Delta(
                                                DeltaType.DifferentValue,
                                                folder.name(),
                                                runId,
                                                -1L,
                                                "transformer_extractors",
                                                matchingExtractor.name(),
                                                transformer.id(),//transformer_extractors do not have an id
                                                matchingExtractor.jsonpath(),
                                                fromExtractor,
                                                extractorNode.name(),
                                                extractorNode.id(),
                                                extractorNode.operation(),
                                                fromH5m
                                        );
                                        deltas.add(d);
                                    }
                                }
                            }
                        }

                        List<LoadLegacyTests.Label> usedLabels = fetchRunLabels(legacyConn, runId);

                        List<Long> datasetIds = getDatasetIds(legacyConn, runId);
                        Node datasetNode = null;
                        List<Value> datasetValues = new ArrayList<>();
                        if(!transformers.isEmpty()){
                            List<Node> datasetNodes = nodeService.findNodeByFqdn("dataset", folder.groupId());
                            if (datasetNodes.isEmpty()) {
                                invocation.println("Cannot find dataset node for " + folder.name() + " groupId=" + folder.groupId());
                                exitCode = CommandResult.FAILURE;
                                continue;
                            }
                            datasetNode = datasetNodes.get(0);
                            datasetValues.addAll(valueService.getDescendantValues(uploadId,List.of(datasetNode.id())));
                        }else{
                            datasetNode = nodeGroup.root();
                            datasetValues.add(valueService.get(uploadId));
                        }

                        if (datasetIds.size() != datasetValues.size()) {
                            invocation.println("INCORRECT NUMBER OF DATASETS h5m=" + datasetValues.size() + " horreum=" + datasetIds.size());
                        }
                        //find the best match between datasets
                        List<DatasetValuePair> pairs = matchDatasetToValue(legacyConn, datasetIds, datasetValues);

                        //compare datasets
                        for (int i = 0; i < pairs.size(); i++) {
                            DatasetValuePair pair = pairs.get(i);
                            long horreumDatasetId = pair.datasetId;
                            long h5mDatasetId = pair.valueId;
                            if (horreumDatasetId == -1 || h5mDatasetId == -1) {
                                Delta d = new Delta(
                                        DeltaType.MissingDataset,
                                        folder.name(),
                                        runId,
                                        horreumDatasetId,
                                        "dataset",
                                        "-",
                                        horreumDatasetId,
                                        "-",
                                        horreumDatasetId == -1 ? JqNull.NULL : JqObject.EMPTY,
                                        "dataset",
                                        h5mDatasetId,
                                        transformers.stream().map(t -> t.name() + "=" + t.id()).collect(Collectors.joining(",")),
                                        h5mDatasetId == -1 ? JqNull.NULL : JqObject.EMPTY
                                );
                                deltas.add(d);
                                continue;
                            }else{
                                datasetToValueId.put(horreumDatasetId, h5mDatasetId);
                            }

                            for (LoadLegacyTests.Label label : usedLabels) {
                                JqValue horreumLabelValue = getLabelValue(legacyConn,horreumDatasetId,label.id());

                                List<Node> matchingNodes = nodeService.findNodeByFqdn(label.name(), folder.groupId());
                                if(matchingNodes.size()>1){
                                    invocation.println("ERROR: too many matching nodes for "+label.name()+"\n"+matchingNodes.stream().map(n->n.name()+"="+n.id()).collect(Collectors.joining(", ")));
                                }
                                if (matchingNodes.isEmpty()) {
                                    invocation.println("ERROR: failed to find match for label " + label.name());
                                    exitCode = CommandResult.FAILURE;
                                    Delta d = new Delta(
                                            DeltaType.MissingLabel,
                                            folder.name(),
                                            runId,
                                            horreumDatasetId,
                                            "label",
                                            label.name(),
                                            label.id(),
                                            label.function(),
                                            horreumLabelValue,
                                            "<missingNode>",
                                            -1L,
                                            "-",
                                            JqNull.NULL
                                    );
                                    deltas.add(d);
                                    continue;
                                }
                                for (Node matchingNode : matchingNodes) {
                                    List<Value> matchingNodeValues = valueService.getDescendantValues(h5mDatasetId,List.of(matchingNode.id()));
                                    if(matchingNodeValues.isEmpty()){
                                        //this means h5m failed to calculate a value
                                    }else if (matchingNodeValues.size() > 1){
                                        //this means h5m calculated too many values
                                    }else{//this is just right
                                        boolean labelValueEq = equalish(horreumLabelValue,matchingNodeValues.getFirst().data());
                                        if(!labelValueEq){
                                            Delta labelValueDelta = new Delta(
                                                    DeltaType.DifferentValue,
                                                    folder.name(),
                                                    runId,
                                                    horreumDatasetId,
                                                    "label",
                                                    label.name(),
                                                    label.id(),
                                                    label.function(),
                                                    horreumLabelValue,
                                                    matchingNode.name(),
                                                    matchingNode.id(),
                                                    matchingNode.operation(),
                                                    matchingNodeValues.getFirst().data()
                                            );
                                            deltas.add(labelValueDelta);
                                            //investigate why the value is different
                                            if(matchingNode.type().equals(NodeType.JS)){
                                                //TODO this fails if the node is a combination node

                                                if(matchingNode.operation().equals(LoadLegacyTests.COMBINE_LABEL_OPERATION)){
                                                    //does one of the combined values match?
                                                    List<JqValue> combinedValues = matchingNode.sources().stream().map(n->{
                                                        List<Value> existing = valueService.getDescendantValues(h5mDatasetId,List.of(n.id()));
                                                        if(existing.size()>0){
                                                            return existing.getFirst().data();
                                                        }else{
                                                            return JqNull.NULL;
                                                        }
                                                    }).toList();
                                                    List<Double> combinedScores = combinedValues.stream().map(v-> score( horreumLabelValue , v )).toList();
                                                    double maxScore = Collections.max(combinedScores);
                                                    double matchingNodeScore = score(horreumLabelValue,matchingNodeValues.get(0).data());
                                                        //this is a better match
                                                    int idx = combinedScores.indexOf(maxScore);
                                                    Log.debug("changing matchingNode for label " + label.name() + " to a combined node = " + matchingNode.sources().get(idx).name() + "=" + matchingNode.sources().get(idx).id() + "\n  horreummValue=" + horreumLabelValue + "\n  combinedNodeValue=" + matchingNodeValues.getFirst().data() + "\n  sourceNodeValue=" + combinedValues.get(idx));
                                                    matchingNode = matchingNode.sources().get(idx);
                                                }
                                                if(matchingNode.sources().size() != label.extractors().size()){
                                                    Log.error("unexpected source difference\n  sources="+matchingNode.sources().stream().map(n->n.name()).collect(Collectors.joining(", "))
                                                    +"\n  extractors="+label.extractors().stream().map(n->n.name()).collect(Collectors.joining(", ")));
                                                }
                                                for (Node node : matchingNode.sources()) {
                                                    var matchingExtractor = label.extractors().stream().filter(e -> equalish(loadLegacyTests.getExtractorRename( e.name() ), node.name())).findFirst().orElse(null);
                                                    if (matchingExtractor == null) {
                                                        if (label.extractors().size() == 1) {
                                                            matchingExtractor = label.extractors().iterator().next();
                                                        } else {
                                                            Log.error("failed to find match for label extractor \"" + node.name() + "\" (" + nameSanitize(node.name()) + ") from: " + label.extractors().stream().map(e -> e.name() + "=(" + nameSanitize(e.name()) + ")").collect(Collectors.joining(", ")));
                                                            exitCode = CommandResult.FAILURE;
                                                            continue;
                                                        }
                                                    }
                                                    JqValue fromExtractor = extractDataset(legacyConn, horreumDatasetId, matchingExtractor.jsonpath(), matchingExtractor.isArray());
                                                    List<Value> h5mValues = valueService.getDescendantValues(h5mDatasetId, List.of(node.id()));
                                                    JqValue fromH5m = h5mValues.isEmpty() ? null : purgeNulls( h5mValues.getFirst().data() );
                                                    boolean eq = equalish(fromExtractor, fromH5m);
                                                    if (!eq) {
                                                        Delta d = new Delta(
                                                                DeltaType.DifferentValue,
                                                                folder.name(),
                                                                runId,
                                                                horreumDatasetId,
                                                                "label_extractors",
                                                                matchingExtractor.name(),
                                                                label.id(),
                                                                matchingExtractor.jsonpath(),
                                                                fromExtractor,
                                                                node.name(),
                                                                node.id(),
                                                                node.operation(),
                                                                fromH5m
                                                        );
                                                        deltas.add(d);
                                                    }
                                                }
                                            }else{
                                                //this delta is already handled before the if block
                                            }
                                        }
                                    }
                                }
                            }
                            //ch ch ch changes
                            List<Change> changes = getChanges(legacyConn,horreumDatasetId);
                            List<Node> detectionNodes = nodeGroup.sources().stream().filter(n->n.type().isDetection()).toList();
                            List<Value> detectionValues = detectionNodes.isEmpty() ? Collections.emptyList() : valueService.getDescendantValues(h5mDatasetId, detectionNodes.stream().map(n->n.id()).collect(Collectors.toList()) );
                            if(detectionValues.size()!= changes.size()){
                                //there a difference in change detection
                                String variableNames = changes.stream().map(c->c.variableName()).distinct().collect(Collectors.joining(", "));

                                Delta changeDiff = new Delta(
                                        DeltaType.MissingChange,
                                        folder.name(),
                                        runId,
                                        horreumDatasetId,
                                        "change",
                                        "<all-changes>",
                                        -1L,
                                        variableNames,
                                        JqNumber.of(changes.size()),
                                        detectionNodes.stream().map(n->n.name()+"="+n.id()).collect(Collectors.joining(", ")),
                                        -1L,
                                        "-",
                                        JqNumber.of(detectionValues.size())
                                );
                                deltas.add(changeDiff);

                            }
                        }
                    }// for runId
                    if (pause) {
                        invocation.println(deltas.size() + " DELTAS for testId="+testId+" "+folder.name());
                        for (Delta d : deltas) {
                            invocation.println(d.toString());
                        }
                        invocation.getShell().readLine(new Prompt("Press Enter to continue..."));
                        deltas.clear();
                    }
                    runIds.clear(); //clear for next loop
                }// for testid
            }
            if(!pause) {
                System.out.println(deltas.size() + " DELTAS");
                for (Delta d : deltas) {
                    invocation.println(d.toString());
                }
            }
            invocation.println(datasetToValueId.size()+" datasets to values: {"+datasetToValueId.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).collect(Collectors.joining(", "))+"}");
        }catch(SQLException e){
            exitCode = CommandResult.FAILURE;
        }
        return exitCode;
    }
    enum DeltaType {DifferentValue,ExtraValue,MissingLabel,MissingDataset,MissingLabelValue,MissingChange,}
    record Delta(DeltaType type,String folderName,long runId,long datasetId,String horreumEntityType,String horreumEntityName,long horreumEntityId,String horreumOperation,JqValue horreumValue,String nodeName,long nodeId,String nodeOperation,JqValue nodeValue){

        @Override
        public String toString() {
            StringBuilder extra = new StringBuilder();
            if(horreumValue !=null && nodeValue!=null && ((horreumValue.toString().length()>=TEXT_LIMIT) || (nodeValue.toString().length()>=TEXT_LIMIT)) ){
                Json horreumJson = horreumValue == null ? new Json() : Json.fromString(horreumValue.toString());
                Json h5mJson = nodeValue == null ? new Json() : Json.fromString(nodeValue.toString());
                JsonComparison comp = new JsonComparison();
                if(horreumJson != null && h5mJson != null){
                    comp.load("hrm", horreumJson);
                    comp.load("h5m", h5mJson);
                    comp.getDiffs().forEach(d -> {
                        extra.append(d.getPath());
                        d.forEach((k, v) -> {
                            extra.append(System.lineSeparator());
                            extra.append(k);
                            extra.append(" : ");
                            extra.append(v);

                        });
                        extra.append(System.lineSeparator());
                    });
                }
            }
            return type+" "+folderName+" runId="+runId+" datasetId="+datasetId+" "+horreumEntityType+"\n"
                    +"  hrm: "+ horreumEntityName+"="+horreumEntityId+"\n"
                    +"    filter: "+horreumOperation+"\n"
                    +"    value: "+(horreumValue==null ? "null" : (horreumValue.toString().length()<TEXT_LIMIT)?horreumValue.toString():("length="+horreumValue.toString().length()))+"\n"
                    +"  h5m: "+nodeName+"="+nodeId+"\n"
                    +"    filter: "+nodeOperation+"\n"
                    +"    value: "+(nodeValue == null ? "null" : (nodeValue.toString().length()<TEXT_LIMIT)?nodeValue.toString():("length="+nodeValue.toString().length()))+"\n"
                    +(extra.length()>0 ?("  diffs:\n    "+extra.toString().replaceAll("\n","\n    ")):"");
        }
    }
    record DatasetValuePair(long datasetId,long valueId){}
    private List<DatasetValuePair> matchDatasetToValue(Connection conn, List<Long> datasetIds,List<Value> values) throws SQLException {
        List<DatasetValuePair> pairs = new ArrayList<>();
        try {

            List<JqObject> datasetLabelValues = new ArrayList<>();
            for (Long id : datasetIds) {
                JqObject jqObject = fetchDatasetLabelValues(conn, id);
                datasetLabelValues.add(jqObject);
            }
            List<JqObject> valueLabelValues = new ArrayList<>();
            for (Value v : values) {
                List<JqObject> jqObjects = valueService.getGroupedValues(
                        v.node().id(),
                        v.id(),
                        Collections.EMPTY_LIST,
                        Collections.EMPTY_MAP,
                        null
                );
                if (jqObjects.isEmpty()) {
                    //this shouldn't happen, what do we do?
                    Log.error("Error: value " + v.id() + " from node=" + v.node().name() + "=" + v.node().id() + " is missing grouped values");
                } else {
                    valueLabelValues.add(jqObjects.getFirst());
                }
            }

            Set<String> datasetKeys = datasetLabelValues.stream().flatMap(o -> o.keys().stream()).collect(Collectors.toSet());
            double scores[][] = new double[datasetLabelValues.size()][valueLabelValues.size()];
            for (int d = 0; d < datasetLabelValues.size(); d++) {
                for (int v = 0; v < valueLabelValues.size(); v++) {
                    scores[d][v] = scoreObjects(datasetLabelValues.get(d), valueLabelValues.get(v));
                }
            }
            //pick the best matches
            Set<Integer> remainingValueIndexes = new HashSet<>(IntStream.rangeClosed(0, valueLabelValues.size() - 1).boxed().toList());

            for (int d = 0; d < datasetLabelValues.size(); d++) {
                int bestIndex = -1;
                double bestScore = -1;
                for (int remainingIndex : remainingValueIndexes) {
                    if (scores[d][remainingIndex] > bestScore) {
                        bestScore = scores[d][remainingIndex];
                        bestIndex = remainingIndex;
                    }
                }
                if (bestIndex != -1) {
                    remainingValueIndexes.remove(bestIndex);
                    DatasetValuePair dpv = new DatasetValuePair(datasetIds.get(d), values.get(bestIndex).id());
                    pairs.add(dpv);
                }
            }
            if (!remainingValueIndexes.isEmpty()) {
                for (int remainingIndex : remainingValueIndexes) {
                    DatasetValuePair dvp = new DatasetValuePair(-1, values.get(remainingIndex).id());
                    pairs.add(dvp);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return pairs;
    }

    record Change(long id,long variableId,long datasetId,String description,String variableName){}
    public List<Change> getChanges(Connection conn,long datasetId) throws SQLException {
        List<Change> changes = new ArrayList<>();
        try(PreparedStatement statement = conn.prepareStatement("select c.id,c.variable_id,c.dataset_id,c.description,v.name from change c join variable v on c.variable_id = v.id where dataset_id = ?")){
            statement.setLong(1, datasetId);
            try(ResultSet rs = statement.executeQuery()){
                while(rs.next()){
                    changes.add(new Change(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return changes;
    }

    private boolean equalish(JqValue horreum, JqValue h5m){
        return horreum.equals(h5m) || (horreum.isNull() && (h5m == null || h5m.isNull()));
    }
    private static boolean equalish(String a,String b){
        return nameSanitize(a).equalsIgnoreCase(nameSanitize(b));
    }
    private static String nameSanitize(String s){
        if(s==null){
            return "null";
        }
        return s.replaceAll("[ \\-_]","").toLowerCase();
    }

    private List<Long> getDatasetIds(Connection connection, Long runId) throws SQLException {
        List<Long> datasetIds = new ArrayList<>();
        try(PreparedStatement statement = connection.prepareStatement("select id from dataset where runid = ?")){
            statement.setLong(1, runId);
            try (ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    datasetIds.add(rs.getLong(1));
                }
            }
        }
        return datasetIds;
    }



    private int getLabelValueCount(Connection connection, Long datasetId) throws SQLException {
        int rtrn = -1;
        try(PreparedStatement statement = connection.prepareStatement("select count(*) from label_values where dataset_id = ? and value is not null")){
            statement.setLong(1, datasetId);
            try (ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    rtrn = rs.getInt(1);
                }
            }
        }
        return rtrn;
    }
    private List<LoadLegacyTests.Label> fetchRunLabels(Connection connection, Long runId) throws SQLException {
        List<LoadLegacyTests.Label> labels = new ArrayList<>();
        List<LoadLegacyTests.LabelDef> labelDefs = new ArrayList<>();
        try(PreparedStatement statement = connection.prepareStatement("select distinct l.id,l.name,l.function from label l where exists (select 1 from label_values v where v.label_id = l.id and v.dataset_id in (select id from dataset where runid = ?))")){
            statement.setLong(1, runId);
            try (ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    labelDefs.add(new LoadLegacyTests.LabelDef(rs.getLong(1),rs.getString(2),rs.getString(3)));
                }
            }
        }
        for(LoadLegacyTests.LabelDef labelDef : labelDefs){
            try(PreparedStatement statement = connection.prepareStatement("select name,jsonpath,isarray from label_extractors where label_id = ?")){
                statement.setLong(1,labelDef.id());
                List<LoadLegacyTests.Extractor> labelExtractors = new ArrayList<>();
                try(ResultSet rs = statement.executeQuery()){
                    while(rs.next()){
                        labelExtractors.add(new LoadLegacyTests.Extractor(rs.getString(1),rs.getString(2),rs.getBoolean(3)));
                    }
                }
                LoadLegacyTests.Label label = new LoadLegacyTests.Label(labelDef.id(),labelDef.name().replaceAll(":","_"),labelDef.function(),labelExtractors);
                labels.add(label);
            }
        }
        return labels;
    }
    private List<Long> fetchTestIds(Connection connection) throws SQLException {
        List<Long> testIds = new ArrayList<>();
        try(PreparedStatement statement = connection.prepareStatement("select id from test order by id")){
            try (ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    testIds.add(rs.getLong(1));
                }
            }
        }
        return testIds;
    }
    private List<Long> fetchRunIds(Connection connection, Long testId,int limit, int offset) throws SQLException {
        List<Long> runIds = new ArrayList<>();
        try(PreparedStatement statement = connection.prepareStatement("select id from run where testid = ? and trashed = false order by id asc limit ? offset ?")){
            statement.setLong(1, testId);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    runIds.add(rs.getLong(1));
                }
            }
        }
        return runIds;
    }
    private JqValue fetchRun(Connection legacyConn,long runId) throws SQLException{
        JqValue rtrn = null;
        try(PreparedStatement stmt = legacyConn.prepareStatement("select data from run where id=?")){
            stmt.setLong(1, runId);
            try (ResultSet rs = stmt.executeQuery()){
               while(rs.next()){
                   if(rtrn !=null){
                       //please no
                   }
                   byte[] bytes = rs.getBytes(1);
                   rtrn = JqValues.parse(bytes);
               }
            }
        }
        return rtrn;
    }

    private JqObject fetchDatasetLabelValues(Connection conn, long datasetId) {
        JqObject value = null;
        try(PreparedStatement ps = conn.prepareStatement(
            """
                WITH
                combined as (
                    SELECT
                        label.name AS labelName,
                        lv.value AS value, runId,
                        dataset.id AS datasetId,
                        dataset.start AS start,
                        dataset.stop AS stop
                    FROM
                        dataset
                        LEFT JOIN label_values lv ON dataset.id = lv.dataset_id
                        LEFT JOIN label ON label.id = lv.label_id
                    WHERE dataset.id = ?
                ), entry as (
                    SELECT
                        runId,
                        datasetId,
                        jsonb_object_agg(labelName,value) as data
                    from
                        combined
                    where labelName is not null
                    group by runId,datasetId
                )
                select
                    jsonb_agg(
                        data -- jsonb_build_object('runId',runId,'datasetId',datasetId,'data',data)
                    ) from entry
            """
        )) {
            ps.setLong(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (value != null) {
                        //This should not happen
                    }
                    String str = rs.getString(1);
                    if(str!=null && !str.isEmpty()){
                        JqArray array = (JqArray) JqValues.parse(rs.getString(1));
                        if(array.isEmpty()){
                            //log this error?
                        }else {
                            value = (JqObject) array.get(0);
                        }
                    }
                }
            }
        } catch (SQLException e){
            Log.error("datasetId = "+datasetId,e);
        }
        return value == null ? JqObject.EMPTY : value;
    }
    private JqArray fetchRunLabelValues(Connection conn, long runId){
        JqValue value = null;
        try(PreparedStatement ps = conn.prepareStatement(
                """
                WITH
                combined as (
                    SELECT
                        label.name AS labelName,
                        lv.value AS value, runId,
                        dataset.id AS datasetId,
                        dataset.start AS start,
                        dataset.stop AS stop
                    FROM 
                        dataset
                        LEFT JOIN label_values lv ON dataset.id = lv.dataset_id
                        LEFT JOIN label ON label.id = lv.label_id
                    WHERE dataset.runid = ?
                ), entry as (
                    SELECT 
                        runId,
                        datasetId,
                        jsonb_object_agg(labelName,value) as data 
                    from 
                        combined 
                    group by runId,datasetId
                ) 
                select 
                    jsonb_agg(                        
                        data -- jsonb_build_object('runId',runId,'datasetId',datasetId,'data',data)
                    ) from entry                        
                """
        )){
            ps.setLong(1, runId);
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()){
                    if(value!=null){
                        //This should not happen
                    }
                    value = JqValues.parse(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if(value.isArray()){
            return (JqArray)value;
        }else{
            return JqArray.EMPTY;
        }
    }
    private static List<LoadLegacyTests.Transformer> loadTransformers(Connection conn,long testId) throws SQLException {
        List<LoadLegacyTests.Transformer> transformers = new ArrayList<>();
        List<Long> transformerIds = new ArrayList<>();
        try(PreparedStatement statement = conn.prepareStatement("select transformer_id from test_transformers where test_id = ?")){
            statement.setLong(1, testId);
            try(ResultSet rs = statement.executeQuery()){
                while(rs.next()){
                    transformerIds.add(rs.getLong(1));
                }
            }
        }
        for(Long transformerId : transformerIds){
            transformers.add(LoadLegacyTests.loadTransformer(conn, transformerId));
        }
        return transformers;
    }
    private static JqValue extractRun(Connection conn, long runId,String jsonpath,boolean array) throws SQLException {
        return extract(conn,runId,jsonpath,"run",array);
    }
    private static JqValue getLabelValue(Connection conn, long datasetId,long labelId) throws SQLException {
        JqValue value = null;
        try(PreparedStatement ps = conn.prepareStatement(
                """
                select value from label_values where dataset_id = ? and label_id = ?
                """
        )){
            ps.setLong(1, datasetId);
            ps.setLong(2, labelId);
            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()){
                    String response = rs.getString(1);
                    if(response!=null && !response.isEmpty()){
                        value = JqValues.parse(response);
                    }
                }
            }
        }
        return value;
    }
    private static JqValue extractDataset(Connection conn, long datasetId,String jsonpath,boolean array) throws SQLException {
        return extract(conn,datasetId,jsonpath,"dataset",array);
    }
    private static JqValue extract(Connection conn, long id,String jsonpath,String target,boolean array) throws SQLException {
        JqValue value = null;
        String op = array?"jsonb_path_query_array":"jsonb_path_query_first";

        try(PreparedStatement ps = conn.prepareStatement(
                """
                select OP(data,?::jsonpath) from TARGET where id = ?;
                """.replaceAll("OP",op).replaceAll("TARGET",target))
        ){
            ps.setString(1, jsonpath);
            ps.setLong(2, id);
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()){
                    if(value != null){
                        //this should not happened
                    }
                    String response = rs.getString(1);
                    if(response==null){
                        //
                    }else {
                        value = JqValues.parse(response);
                    }
                }
            }
        }
        if(value == null){
            return JqNull.NULL;
        }
        return value;
    }
    private String s(Object o){
        if(o == null){
            return "";
        }
        int limit = 180;
        String s = o.toString();
        if(s.length()>limit){
            return s.length()+": "+s.substring(0,limit);
        }else
            return s;
    }
    private double score(JqValue a,JqValue b){
        if( a.isObject() && b.isObject()){
            return scoreObjects((JqObject)a,(JqObject)b);
        }else{
            //using Levenshtein edit distance as percent of total length Distance
            String s1 = a.toString();
            String s2 = b.toString();
            if (s1.length() > s2.length()) {
                String temp = s1;
                s1 = s2;
                s2 = temp;
            }

            int l1 = s1.length();
            int l2 = s2.length();

            // Arrays to store costs of the current and previous rows
            int[] prevRow = new int[l1 + 1];
            int[] currRow = new int[l1 + 1];

            // Initialize the base case for the first row
            for (int i = 0; i <= l1; i++) {
                prevRow[i] = i;
            }

            // Iteratively fill the rows
            for (int j = 1; j <= l2; j++) {
                currRow[0] = j; // Deletion cost from s2

                for (int i = 1; i <= l1; i++) {
                    // If characters match, cost is 0, otherwise 1
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                    // Calculate minimum of insertion, deletion, and substitution
                    currRow[i] = Math.min(
                            Math.min(currRow[i - 1] + 1,   // Insertion
                                    prevRow[i] + 1),      // Deletion
                            prevRow[i - 1] + cost          // Substitution
                    );
                }

                // Move the current row data to the previous row for the next iteration
                System.arraycopy(currRow, 0, prevRow, 0, prevRow.length);
            }

            return ( (double)Math.max(s1.length(), s2.length()) - prevRow[l1] ) / (double) Math.max(s1.length(), s2.length());
        }
    }
    private double scoreObjects(JqObject a, JqObject b){
        int div = Math.max(a.length(),b.length());
        Set<String> keys = Sets.join(a.keys(),b.keys());
        int count = 0;
        for(String k : keys){
            if(a.has(k) && b.has(k) && a.get(k).equals(b.get(k))){
                count++;
            }
        }
        return count / (double)div;
    }
    private String getTestName(Connection conn, long testId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM test WHERE id = ?")) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
    private List<Long> getRunIds(Connection conn, long testId, int limit,int offset) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM run WHERE testid = ? AND trashed = false ORDER BY id DESC LIMIT ? OFFSET ?")) {
            ps.setLong(1, testId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }
}
