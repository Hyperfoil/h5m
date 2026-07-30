package io.hyperfoil.tools.h5m.provided;

import org.eclipse.microprofile.config.ConfigProvider;
import java.util.Optional;
import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * Hibernate dialect that delegates to either {@link SQLiteDialect} or {@link PostgreSQLDialect}
 * based on the JDBC URL configured at runtime.
 * <p>
 * Since {@code quarkus.datasource.db-kind} is a build-time property, Quarkus locks the dialect
 * at build time. This wrapper is set via {@code quarkus.hibernate-orm.dialect} and resolves the
 * actual dialect at application startup from the JDBC URL.
 */
@SuppressWarnings({"deprecation", "removal"})
public class H5mDialect extends Dialect {

    private static final ThreadLocal<Dialect> CONSTRUCTING = new ThreadLocal<>();

    private final Dialect delegate;

    public H5mDialect(DialectResolutionInfo info) {
        super(createDelegate(info));
        delegate = CONSTRUCTING.get();
        CONSTRUCTING.remove();
    }

    public H5mDialect() {
        super(createDelegate());
        delegate = CONSTRUCTING.get();
        CONSTRUCTING.remove();
    }

    private static boolean isSQLite() {
        Optional<String> url = ConfigProvider.getConfig().getOptionalValue("quarkus.datasource.jdbc.url", String.class);
        return url.isPresent() && url.get().startsWith("jdbc:sqlite:");
    }

    private static DialectResolutionInfo createDelegate(DialectResolutionInfo info) {
        CONSTRUCTING.set(isSQLite() ? new SQLiteDialect(info) : new PostgreSQLDialect(info));
        return info;
    }

    private static DialectResolutionInfo createDelegate() {
        CONSTRUCTING.set(isSQLite() ? new SQLiteDialect() : new PostgreSQLDialect());
        return null;
    }

    private Dialect d() {
        Dialect d = delegate;
        return d != null ? d : CONSTRUCTING.get();
    }

    // --- generated delegate methods ---

    @Override public org.hibernate.dialect.DatabaseVersion determineDatabaseVersion(org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo a0) { return d().determineDatabaseVersion(a0); }
    @Override public boolean stripsTrailingSpacesFromChar() { return d().stripsTrailingSpacesFromChar(); }
    @Override public org.hibernate.dialect.DatabaseVersion getVersion() { return d().getVersion(); }
    @Override public org.hibernate.type.descriptor.jdbc.JdbcType resolveSqlTypeDescriptor(java.lang.String a0, int a1, int a2, int a3, org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry a4) { return d().resolveSqlTypeDescriptor(a0, a1, a2, a3, a4); }
    @Override public int resolveSqlTypeLength(java.lang.String a0, int a1, int a2, int a3, int a4) { return d().resolveSqlTypeLength(a0, a1, a2, a3, a4); }
    @Override public java.lang.String getEnumTypeDeclaration(java.lang.String a0, java.lang.String[] a1) { return d().getEnumTypeDeclaration(a0, a1); }
    @Override public java.lang.String getEnumTypeDeclaration(java.lang.Class<? extends java.lang.Enum<?>> a0) { return d().getEnumTypeDeclaration(a0); }
    @Override public java.lang.String[] getCreateEnumTypeCommand(java.lang.String a0, java.lang.String[] a1) { return d().getCreateEnumTypeCommand(a0, a1); }
    @Override public java.lang.String[] getCreateEnumTypeCommand(java.lang.Class<? extends java.lang.Enum<?>> a0) { return d().getCreateEnumTypeCommand(a0); }
    @Override public java.lang.String[] getDropEnumTypeCommand(java.lang.String a0) { return d().getDropEnumTypeCommand(a0); }
    @Override public java.lang.String[] getDropEnumTypeCommand(java.lang.Class<? extends java.lang.Enum<?>> a0) { return d().getDropEnumTypeCommand(a0); }
    @Override public java.lang.String getCheckCondition(java.lang.String a0, java.lang.String[] a1) { return d().getCheckCondition(a0, a1); }
    @Override public java.lang.String getCheckCondition(java.lang.String a0, java.lang.Class<? extends java.lang.Enum<?>> a1) { return d().getCheckCondition(a0, a1); }
    @Override public java.lang.String getCheckCondition(java.lang.String a0, long a1, long a2) { return d().getCheckCondition(a0, a1, a2); }
    @Override public java.lang.String getCheckCondition(java.lang.String a0, long[] a1) { return d().getCheckCondition(a0, a1); }
    @Override public java.lang.String getCheckCondition(java.lang.String a0, java.lang.Long[] a1) { return d().getCheckCondition(a0, a1); }
    @Override public java.lang.String getCheckCondition(java.lang.String a0, java.util.Collection<?> a1, org.hibernate.type.descriptor.jdbc.JdbcType a2) { return d().getCheckCondition(a0, a1, a2); }
    @Override public void contributeFunctions(org.hibernate.boot.model.FunctionContributions a0) { d().contributeFunctions(a0); }
    @Override public int ordinal() { return d().ordinal(); }
    @Override public void initializeFunctionRegistry(org.hibernate.boot.model.FunctionContributions a0) { d().initializeFunctionRegistry(a0); }
    @Override public java.lang.String currentDate() { return d().currentDate(); }
    @Override public java.lang.String currentTime() { return d().currentTime(); }
    @Override public java.lang.String currentTimestamp() { return d().currentTimestamp(); }
    @Override public java.lang.String currentLocalTime() { return d().currentLocalTime(); }
    @Override public java.lang.String currentLocalTimestamp() { return d().currentLocalTimestamp(); }
    @Override public java.lang.String currentTimestampWithTimeZone() { return d().currentTimestampWithTimeZone(); }
    @Override public java.lang.String extractPattern(org.hibernate.query.common.TemporalUnit a0) { return d().extractPattern(a0); }
    @Override public java.lang.String castPattern(org.hibernate.query.sqm.CastType a0, org.hibernate.query.sqm.CastType a1) { return d().castPattern(a0, a1); }
    @Override public java.lang.String getDual() { return d().getDual(); }
    @Override public java.lang.String getFromDualForSelectOnly() { return d().getFromDualForSelectOnly(); }
    @Override public java.lang.String trimPattern(org.hibernate.query.sqm.TrimSpec a0, boolean a1) { return d().trimPattern(a0, a1); }
    @Override public boolean supportsFractionalTimestampArithmetic() { return d().supportsFractionalTimestampArithmetic(); }
    @Override public java.lang.String timestampdiffPattern(org.hibernate.query.common.TemporalUnit a0, jakarta.persistence.TemporalType a1, jakarta.persistence.TemporalType a2) { return d().timestampdiffPattern(a0, a1, a2); }
    @Override public java.lang.String timestampaddPattern(org.hibernate.query.common.TemporalUnit a0, jakarta.persistence.TemporalType a1, org.hibernate.query.sqm.IntervalType a2) { return d().timestampaddPattern(a0, a1, a2); }
    @Override public boolean equivalentTypes(int a0, int a1) { return d().equivalentTypes(a0, a1); }
    @Override public java.util.Properties getDefaultProperties() { return d().getDefaultProperties(); }
    @Override public int getDefaultStatementBatchSize() { return d().getDefaultStatementBatchSize(); }
    @Override public boolean getDefaultNonContextualLobCreation() { return d().getDefaultNonContextualLobCreation(); }
    @Override public boolean getDefaultUseGetGeneratedKeys() { return d().getDefaultUseGetGeneratedKeys(); }
    @Override public void contribute(org.hibernate.boot.model.TypeContributions a0, org.hibernate.service.ServiceRegistry a1) { d().contribute(a0, a1); }
    @Override public void contributeTypes(org.hibernate.boot.model.TypeContributions a0, org.hibernate.service.ServiceRegistry a1) { d().contributeTypes(a0, a1); }
    @Override public org.hibernate.dialect.LobMergeStrategy getLobMergeStrategy() { return d().getLobMergeStrategy(); }
    @Override public java.lang.String getNativeIdentifierGeneratorStrategy() { return d().getNativeIdentifierGeneratorStrategy(); }
    @Override public jakarta.persistence.GenerationType getNativeValueGenerationStrategy() { return d().getNativeValueGenerationStrategy(); }
    @Override public org.hibernate.dialect.identity.IdentityColumnSupport getIdentityColumnSupport() { return d().getIdentityColumnSupport(); }
    @Override public org.hibernate.dialect.sequence.SequenceSupport getSequenceSupport() { return d().getSequenceSupport(); }
    @Override public java.lang.String getQuerySequencesString() { return d().getQuerySequencesString(); }
    @Override public org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor getSequenceInformationExtractor() { return d().getSequenceInformationExtractor(); }
    @Override public org.hibernate.tool.schema.extract.spi.InformationExtractor getInformationExtractor(org.hibernate.tool.schema.extract.spi.ExtractionContext a0) { return d().getInformationExtractor(a0); }
    @Override public java.lang.String getSelectGUIDString() { return d().getSelectGUIDString(); }
    @Override public boolean supportsTemporaryTables() { return d().supportsTemporaryTables(); }
    @Override public boolean supportsTemporaryTablePrimaryKey() { return d().supportsTemporaryTablePrimaryKey(); }
    @Override public org.hibernate.dialect.pagination.LimitHandler getLimitHandler() { return d().getLimitHandler(); }
    @Override public org.hibernate.dialect.lock.spi.LockingSupport getLockingSupport() { return d().getLockingSupport(); }
    @Override public boolean supportsForUpdate() { return d().supportsForUpdate(); }
    @Override public boolean supportsSkipLocked() { return d().supportsSkipLocked(); }
    @Override public boolean supportsNoWait() { return d().supportsNoWait(); }
    @Override public boolean supportsWait() { return d().supportsWait(); }
    @Override public boolean useFollowOnLocking(java.lang.String a0, org.hibernate.query.spi.QueryOptions a1) { return d().useFollowOnLocking(a0, a1); }
    @Override public org.hibernate.dialect.lock.PessimisticLockStyle getPessimisticLockStyle() { return d().getPessimisticLockStyle(); }
    @Override public org.hibernate.dialect.RowLockStrategy getWriteRowLockStrategy() { return d().getWriteRowLockStrategy(); }
    @Override public org.hibernate.dialect.RowLockStrategy getReadRowLockStrategy() { return d().getReadRowLockStrategy(); }
    @Override public org.hibernate.sql.ast.spi.LockingClauseStrategy getLockingClauseStrategy(org.hibernate.sql.ast.tree.select.QuerySpec a0, org.hibernate.LockOptions a1) { return d().getLockingClauseStrategy(a0, a1); }
    @Override public org.hibernate.dialect.lock.LockingStrategy getLockingStrategy(org.hibernate.persister.entity.EntityPersister a0, org.hibernate.LockMode a1, org.hibernate.Locking.Scope a2) { return d().getLockingStrategy(a0, a1, a2); }
    @Override public org.hibernate.dialect.lock.LockingStrategy getLockingStrategy(org.hibernate.persister.entity.EntityPersister a0, org.hibernate.LockMode a1) { return d().getLockingStrategy(a0, a1); }
    @Override public java.lang.String getForUpdateString(org.hibernate.LockOptions a0) { return d().getForUpdateString(a0); }
    @Override public java.lang.String getForUpdateString(org.hibernate.LockMode a0, jakarta.persistence.Timeout a1) { return d().getForUpdateString(a0, a1); }
    @Override public java.lang.String getForUpdateString(org.hibernate.LockMode a0, int a1) { return d().getForUpdateString(a0, a1); }
    @Override public java.lang.String getForUpdateString(org.hibernate.LockMode a0) { return d().getForUpdateString(a0); }
    @Override public java.lang.String getForUpdateString() { return d().getForUpdateString(); }
    @Override public java.lang.String getWriteLockString(jakarta.persistence.Timeout a0) { return d().getWriteLockString(a0); }
    @Override public java.lang.String getWriteLockString(int a0) { return d().getWriteLockString(a0); }
    @Override public java.lang.String getWriteLockString(java.lang.String a0, jakarta.persistence.Timeout a1) { return d().getWriteLockString(a0, a1); }
    @Override public java.lang.String getWriteLockString(java.lang.String a0, int a1) { return d().getWriteLockString(a0, a1); }
    @Override public java.lang.String getReadLockString(jakarta.persistence.Timeout a0) { return d().getReadLockString(a0); }
    @Override public java.lang.String getReadLockString(int a0) { return d().getReadLockString(a0); }
    @Override public java.lang.String getReadLockString(java.lang.String a0, jakarta.persistence.Timeout a1) { return d().getReadLockString(a0, a1); }
    @Override public java.lang.String getReadLockString(java.lang.String a0, int a1) { return d().getReadLockString(a0, a1); }
    @Override public java.lang.String getForUpdateString(java.lang.String a0) { return d().getForUpdateString(a0); }
    @Override public java.lang.String getForUpdateString(java.lang.String a0, org.hibernate.LockOptions a1) { return d().getForUpdateString(a0, a1); }
    @Override public java.lang.String getForUpdateNowaitString() { return d().getForUpdateNowaitString(); }
    @Override public java.lang.String getForUpdateSkipLockedString() { return d().getForUpdateSkipLockedString(); }
    @Override public java.lang.String getForUpdateString(jakarta.persistence.Timeout a0) { return d().getForUpdateString(a0); }
    @Override public java.lang.String getForUpdateNowaitString(java.lang.String a0) { return d().getForUpdateNowaitString(a0); }
    @Override public java.lang.String getForUpdateSkipLockedString(java.lang.String a0) { return d().getForUpdateSkipLockedString(a0); }
    @Override public java.lang.String appendLockHint(org.hibernate.LockOptions a0, java.lang.String a1) { return d().appendLockHint(a0, a1); }
    @Override public java.lang.String applyLocksToSql(java.lang.String a0, org.hibernate.LockOptions a1, java.util.Map<java.lang.String, java.lang.String[]> a2) { return d().applyLocksToSql(a0, a1, a2); }
    @Override public boolean supportsOuterJoinForUpdate() { return d().supportsOuterJoinForUpdate(); }
    @Override public boolean supportsLockTimeouts() { return d().supportsLockTimeouts(); }
    @Override public java.lang.String getCreateTableString() { return d().getCreateTableString(); }
    @Override public java.lang.String getTableTypeString() { return d().getTableTypeString(); }
    @Override public boolean supportsIfExistsBeforeTableName() { return d().supportsIfExistsBeforeTableName(); }
    @Override public boolean supportsIfExistsAfterTableName() { return d().supportsIfExistsAfterTableName(); }
    @Override public java.lang.String getBeforeDropStatement() { return d().getBeforeDropStatement(); }
    @Override public java.lang.String getDropTableString(java.lang.String a0) { return d().getDropTableString(a0); }
    @Override public java.lang.String getCreateIndexString(boolean a0) { return d().getCreateIndexString(a0); }
    @Override public java.lang.String getCreateIndexTail(boolean a0, java.util.List<org.hibernate.mapping.Column> a1) { return d().getCreateIndexTail(a0, a1); }
    @Override public boolean qualifyIndexName() { return d().qualifyIndexName(); }
    @Override public java.lang.String getCreateMultisetTableString() { return d().getCreateMultisetTableString(); }
    @Override public boolean hasAlterTable() { return d().hasAlterTable(); }
    @Override public java.lang.String getAlterTableString(java.lang.String a0) { return d().getAlterTableString(a0); }
    @Override public boolean supportsIfExistsAfterAlterTable() { return d().supportsIfExistsAfterAlterTable(); }
    @Override public java.lang.String getAddColumnString() { return d().getAddColumnString(); }
    @Override public java.lang.String getAddColumnSuffixString() { return d().getAddColumnSuffixString(); }
    @Override public boolean dropConstraints() { return d().dropConstraints(); }
    @Override public java.lang.String getDropForeignKeyString() { return d().getDropForeignKeyString(); }
    @Override public java.lang.String getDropUniqueKeyString() { return d().getDropUniqueKeyString(); }
    @Override public boolean supportsIfExistsBeforeConstraintName() { return d().supportsIfExistsBeforeConstraintName(); }
    @Override public boolean supportsIfExistsAfterConstraintName() { return d().supportsIfExistsAfterConstraintName(); }
    @Override public boolean supportsAlterColumnType() { return d().supportsAlterColumnType(); }
    @Override public java.lang.String getAlterColumnTypeString(java.lang.String a0, java.lang.String a1, java.lang.String a2) { return d().getAlterColumnTypeString(a0, a1, a2); }
    @Override public java.lang.String getAddForeignKeyConstraintString(java.lang.String a0, java.lang.String[] a1, java.lang.String a2, java.lang.String[] a3, boolean a4) { return d().getAddForeignKeyConstraintString(a0, a1, a2, a3, a4); }
    @Override public java.lang.String getAddForeignKeyConstraintString(java.lang.String a0, java.lang.String a1) { return d().getAddForeignKeyConstraintString(a0, a1); }
    @Override public boolean useCrossReferenceForeignKeys() { return d().useCrossReferenceForeignKeys(); }
    @Override public java.lang.String getCrossReferenceParentTableFilter() { return d().getCrossReferenceParentTableFilter(); }
    @Override public java.lang.String getAddPrimaryKeyConstraintString(java.lang.String a0) { return d().getAddPrimaryKeyConstraintString(a0); }
    @Override public boolean requiresColumnListInCreateView() { return d().requiresColumnListInCreateView(); }
    @Override public org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy getFallbackSqmMutationStrategy(org.hibernate.metamodel.mapping.EntityMappingType a0, org.hibernate.metamodel.spi.RuntimeModelCreationContext a1) { return d().getFallbackSqmMutationStrategy(a0, a1); }
    @Override public org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy getFallbackSqmInsertStrategy(org.hibernate.metamodel.mapping.EntityMappingType a0, org.hibernate.metamodel.spi.RuntimeModelCreationContext a1) { return d().getFallbackSqmInsertStrategy(a0, a1); }
    @Override public java.lang.String getCreateUserDefinedTypeKindString() { return d().getCreateUserDefinedTypeKindString(); }
    @Override public java.lang.String getCreateUserDefinedTypeExtensionsString() { return d().getCreateUserDefinedTypeExtensionsString(); }
    @Override public boolean supportsIfExistsBeforeTypeName() { return d().supportsIfExistsBeforeTypeName(); }
    @Override public boolean supportsIfExistsAfterTypeName() { return d().supportsIfExistsAfterTypeName(); }
    @Override public java.lang.String getCatalogSeparator() { return d().getCatalogSeparator(); }
    @Override public int registerResultSetOutParameter(java.sql.CallableStatement a0, int a1) throws java.sql.SQLException { return d().registerResultSetOutParameter(a0, a1); }
    @Override public int registerResultSetOutParameter(java.sql.CallableStatement a0, java.lang.String a1) throws java.sql.SQLException { return d().registerResultSetOutParameter(a0, a1); }
    @Override public java.sql.ResultSet getResultSet(java.sql.CallableStatement a0) throws java.sql.SQLException { return d().getResultSet(a0); }
    @Override public java.sql.ResultSet getResultSet(java.sql.CallableStatement a0, int a1) throws java.sql.SQLException { return d().getResultSet(a0, a1); }
    @Override public java.sql.ResultSet getResultSet(java.sql.CallableStatement a0, java.lang.String a1) throws java.sql.SQLException { return d().getResultSet(a0, a1); }
    @Override public boolean supportsCurrentTimestampSelection() { return d().supportsCurrentTimestampSelection(); }
    @Override public boolean isCurrentTimestampSelectStringCallable() { return d().isCurrentTimestampSelectStringCallable(); }
    @Override public java.lang.String getCurrentTimestampSelectString() { return d().getCurrentTimestampSelectString(); }
    @Override public boolean supportsStandardCurrentTimestampFunction() { return d().supportsStandardCurrentTimestampFunction(); }
    @Override public org.hibernate.exception.spi.SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() { return d().buildSQLExceptionConversionDelegate(); }
    @Override public org.hibernate.exception.spi.ViolatedConstraintNameExtractor getViolatedConstraintNameExtractor() { return d().getViolatedConstraintNameExtractor(); }
    @Override public java.lang.String getSelectClauseNullString(int a0, org.hibernate.type.spi.TypeConfiguration a1) { return d().getSelectClauseNullString(a0, a1); }
    @Override public java.lang.String getSelectClauseNullString(org.hibernate.metamodel.mapping.SqlTypedMapping a0, org.hibernate.type.spi.TypeConfiguration a1) { return d().getSelectClauseNullString(a0, a1); }
    @Override public boolean supportsUnionAll() { return d().supportsUnionAll(); }
    @Override public boolean supportsUnionInSubquery() { return d().supportsUnionInSubquery(); }
    @Override public java.lang.String getSetOperatorSqlString(org.hibernate.query.sqm.SetOperator a0) { return d().getSetOperatorSqlString(a0); }
    @Override public java.lang.String getNoColumnsInsertString() { return d().getNoColumnsInsertString(); }
    @Override public boolean supportsNoColumnsInsert() { return d().supportsNoColumnsInsert(); }
    @Override public java.lang.String getLowercaseFunction() { return d().getLowercaseFunction(); }
    @Override public java.lang.String getCaseInsensitiveLike() { return d().getCaseInsensitiveLike(); }
    @Override public boolean supportsCaseInsensitiveLike() { return d().supportsCaseInsensitiveLike(); }
    @Override public boolean supportsTruncateWithCast() { return d().supportsTruncateWithCast(); }
    @Override public boolean supportsIsTrue() { return d().supportsIsTrue(); }
    @Override public java.lang.String transformSelectString(java.lang.String a0) { return d().transformSelectString(a0); }
    @Override public int getMaxAliasLength() { return d().getMaxAliasLength(); }
    @Override public int getMaxIdentifierLength() { return d().getMaxIdentifierLength(); }
    @Override public java.lang.String toBooleanValueString(boolean a0) { return d().toBooleanValueString(a0); }
    @Override public void appendBooleanValueString(org.hibernate.sql.ast.spi.SqlAppender a0, boolean a1) { d().appendBooleanValueString(a0, a1); }
    @Override public java.util.Set<java.lang.String> getKeywords() { return d().getKeywords(); }
    @Override public org.hibernate.engine.jdbc.env.spi.IdentifierHelper buildIdentifierHelper(org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder a0, java.sql.DatabaseMetaData a1) throws java.sql.SQLException { return d().buildIdentifierHelper(a0, a1); }
    @Override public char openQuote() { return d().openQuote(); }
    @Override public char closeQuote() { return d().closeQuote(); }
    @Override public java.lang.String toQuotedIdentifier(java.lang.String a0) { return d().toQuotedIdentifier(a0); }
    @Override public java.lang.String quote(java.lang.String a0) { return d().quote(a0); }
    @Override public org.hibernate.tool.schema.spi.SchemaManagementTool getFallbackSchemaManagementTool(java.util.Map<java.lang.String, java.lang.Object> a0, org.hibernate.service.spi.ServiceRegistryImplementor a1) { return d().getFallbackSchemaManagementTool(a0, a1); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.mapping.Table> getTableExporter() { return d().getTableExporter(); }
    @Override public org.hibernate.tool.schema.spi.TableMigrator getTableMigrator() { return d().getTableMigrator(); }
    @Override public org.hibernate.tool.schema.spi.Cleaner getTableCleaner() { return d().getTableCleaner(); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.mapping.UserDefinedType> getUserDefinedTypeExporter() { return d().getUserDefinedTypeExporter(); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.boot.model.relational.Sequence> getSequenceExporter() { return d().getSequenceExporter(); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.mapping.Index> getIndexExporter() { return d().getIndexExporter(); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.mapping.ForeignKey> getForeignKeyExporter() { return d().getForeignKeyExporter(); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.mapping.UniqueKey> getUniqueKeyExporter() { return d().getUniqueKeyExporter(); }
    @Override public org.hibernate.tool.schema.spi.Exporter<org.hibernate.boot.model.relational.AuxiliaryDatabaseObject> getAuxiliaryDatabaseObjectExporter() { return d().getAuxiliaryDatabaseObjectExporter(); }
    @Override public org.hibernate.dialect.temptable.TemporaryTableExporter getTemporaryTableExporter() { return d().getTemporaryTableExporter(); }
    @Override public org.hibernate.dialect.temptable.TemporaryTableStrategy getPersistentTemporaryTableStrategy() { return d().getPersistentTemporaryTableStrategy(); }
    @Override public org.hibernate.dialect.temptable.TemporaryTableStrategy getLocalTemporaryTableStrategy() { return d().getLocalTemporaryTableStrategy(); }
    @Override public org.hibernate.dialect.temptable.TemporaryTableStrategy getGlobalTemporaryTableStrategy() { return d().getGlobalTemporaryTableStrategy(); }
    @Override public org.hibernate.dialect.temptable.TemporaryTableKind getSupportedTemporaryTableKind() { return d().getSupportedTemporaryTableKind(); }
    @Override public java.lang.String getTemporaryTableCreateOptions() { return d().getTemporaryTableCreateOptions(); }
    @Override public java.lang.String getTemporaryTableCreateCommand() { return d().getTemporaryTableCreateCommand(); }
    @Override public java.lang.String getTemporaryTableDropCommand() { return d().getTemporaryTableDropCommand(); }
    @Override public java.lang.String getTemporaryTableTruncateCommand() { return d().getTemporaryTableTruncateCommand(); }
    @Override public java.lang.String getCreateTemporaryTableColumnAnnotation(int a0) { return d().getCreateTemporaryTableColumnAnnotation(a0); }
    @Override public org.hibernate.boot.TempTableDdlTransactionHandling getTemporaryTableDdlTransactionHandling() { return d().getTemporaryTableDdlTransactionHandling(); }
    @Override public org.hibernate.query.sqm.mutation.spi.AfterUseAction getTemporaryTableAfterUseAction() { return d().getTemporaryTableAfterUseAction(); }
    @Override public org.hibernate.query.sqm.mutation.spi.BeforeUseAction getTemporaryTableBeforeUseAction() { return d().getTemporaryTableBeforeUseAction(); }
    @Override public boolean canCreateCatalog() { return d().canCreateCatalog(); }
    @Override public java.lang.String[] getCreateCatalogCommand(java.lang.String a0) { return d().getCreateCatalogCommand(a0); }
    @Override public java.lang.String[] getDropCatalogCommand(java.lang.String a0) { return d().getDropCatalogCommand(a0); }
    @Override public boolean canCreateSchema() { return d().canCreateSchema(); }
    @Override public java.lang.String[] getCreateSchemaCommand(java.lang.String a0) { return d().getCreateSchemaCommand(a0); }
    @Override public java.lang.String[] getDropSchemaCommand(java.lang.String a0) { return d().getDropSchemaCommand(a0); }
    @Override public java.lang.String getCurrentSchemaCommand() { return d().getCurrentSchemaCommand(); }
    @Override public org.hibernate.engine.jdbc.env.spi.SchemaNameResolver getSchemaNameResolver() { return d().getSchemaNameResolver(); }
    @Override public boolean hasSelfReferentialForeignKeyBug() { return d().hasSelfReferentialForeignKeyBug(); }
    @Override public java.lang.String getNullColumnString() { return d().getNullColumnString(); }
    @Override public java.lang.String getNullColumnString(java.lang.String a0) { return d().getNullColumnString(a0); }
    @Override public java.lang.String quoteCollation(java.lang.String a0) { return d().quoteCollation(a0); }
    @Override public boolean supportsCommentOn() { return d().supportsCommentOn(); }
    @Override public java.lang.String getTableComment(java.lang.String a0) { return d().getTableComment(a0); }
    @Override public java.lang.String getColumnComment(java.lang.String a0) { return d().getColumnComment(a0); }
    @Override public boolean supportsColumnCheck() { return d().supportsColumnCheck(); }
    @Override public boolean supportsNamedColumnCheck() { return d().supportsNamedColumnCheck(); }
    @Override public boolean supportsTableCheck() { return d().supportsTableCheck(); }
    @Override public boolean supportsCascadeDelete() { return d().supportsCascadeDelete(); }
    @Override public java.lang.String getCascadeConstraintsString() { return d().getCascadeConstraintsString(); }
    @Override public org.hibernate.dialect.ColumnAliasExtractor getColumnAliasExtractor() { return d().getColumnAliasExtractor(); }
    @Override public boolean useInputStreamToInsertBlob() { return d().useInputStreamToInsertBlob(); }
    @Override public boolean useConnectionToCreateLob() { return d().useConnectionToCreateLob(); }
    @Override public boolean supportsOrdinalSelectItemReference() { return d().supportsOrdinalSelectItemReference(); }
    @Override public org.hibernate.dialect.NullOrdering getNullOrdering() { return d().getNullOrdering(); }
    @Override public boolean supportsNullPrecedence() { return d().supportsNullPrecedence(); }
    @Override public boolean requiresCastForConcatenatingNonStrings() { return d().requiresCastForConcatenatingNonStrings(); }
    @Override public boolean requiresFloatCastingOfIntegerDivision() { return d().requiresFloatCastingOfIntegerDivision(); }
    @Override public boolean supportsResultSetPositionQueryMethodsOnForwardOnlyCursor() { return d().supportsResultSetPositionQueryMethodsOnForwardOnlyCursor(); }
    @Override public boolean supportsCircularCascadeDeleteConstraints() { return d().supportsCircularCascadeDeleteConstraints(); }
    @Override public boolean supportsSubselectAsInPredicateLHS() { return d().supportsSubselectAsInPredicateLHS(); }
    @Override public boolean supportsExpectedLobUsagePattern() { return d().supportsExpectedLobUsagePattern(); }
    @Override public boolean supportsLobValueChangePropagation() { return d().supportsLobValueChangePropagation(); }
    @Override public boolean supportsUnboundedLobLocatorMaterialization() { return d().supportsUnboundedLobLocatorMaterialization(); }
    @Override public boolean supportsSubqueryOnMutatingTable() { return d().supportsSubqueryOnMutatingTable(); }
    @Override public boolean supportsExistsInSelect() { return d().supportsExistsInSelect(); }
    @Override public boolean doesReadCommittedCauseWritersToBlockReaders() { return d().doesReadCommittedCauseWritersToBlockReaders(); }
    @Override public boolean doesRepeatableReadCauseReadersToBlockWriters() { return d().doesRepeatableReadCauseReadersToBlockWriters(); }
    @Override public boolean supportsBindAsCallableArgument() { return d().supportsBindAsCallableArgument(); }
    @Override public boolean supportsTupleCounts() { return d().supportsTupleCounts(); }
    @Override public boolean requiresParensForTupleCounts() { return d().requiresParensForTupleCounts(); }
    @Override public boolean supportsTupleDistinctCounts() { return d().supportsTupleDistinctCounts(); }
    @Override public boolean requiresParensForTupleDistinctCounts() { return d().requiresParensForTupleDistinctCounts(); }
    @Override public int getInExpressionCountLimit() { return d().getInExpressionCountLimit(); }
    @Override public int getParameterCountLimit() { return d().getParameterCountLimit(); }
    @Override public boolean forceLobAsLastValue() { return d().forceLobAsLastValue(); }
    @Override public boolean isEmptyStringTreatedAsNull() { return d().isEmptyStringTreatedAsNull(); }
    @Override public org.hibernate.dialect.unique.UniqueDelegate getUniqueDelegate() { return d().getUniqueDelegate(); }
    @Override public java.lang.String getQueryHintString(java.lang.String a0, java.util.List<java.lang.String> a1) { return d().getQueryHintString(a0, a1); }
    @Override public java.lang.String getQueryHintString(java.lang.String a0, java.lang.String a1) { return d().getQueryHintString(a0, a1); }
    @Override public org.hibernate.ScrollMode defaultScrollMode() { return d().defaultScrollMode(); }
    @Override public boolean supportsOffsetInSubquery() { return d().supportsOffsetInSubquery(); }
    @Override public boolean supportsOrderByInSubquery() { return d().supportsOrderByInSubquery(); }
    @Override public boolean supportsSubqueryInSelect() { return d().supportsSubqueryInSelect(); }
    @Override public boolean supportsInsertReturning() { return d().supportsInsertReturning(); }
    @Override public boolean supportsInsertReturningRowId() { return d().supportsInsertReturningRowId(); }
    @Override public boolean supportsUpdateReturning() { return d().supportsUpdateReturning(); }
    @Override public boolean supportsInsertReturningGeneratedKeys() { return d().supportsInsertReturningGeneratedKeys(); }
    @Override public boolean unquoteGetGeneratedKeys() { return d().unquoteGetGeneratedKeys(); }
    @Override public boolean supportsFetchClause(org.hibernate.query.common.FetchClauseType a0) { return d().supportsFetchClause(a0); }
    @Override public boolean supportsWindowFunctions() { return d().supportsWindowFunctions(); }
    @Override public boolean supportsLateral() { return d().supportsLateral(); }
    @Override public org.hibernate.procedure.spi.CallableStatementSupport getCallableStatementSupport() { return d().getCallableStatementSupport(); }
    @Override public org.hibernate.engine.jdbc.env.spi.NameQualifierSupport getNameQualifierSupport() { return d().getNameQualifierSupport(); }
    @Override public org.hibernate.loader.ast.spi.MultiKeyLoadSizingStrategy getMultiKeyLoadSizingStrategy() { return d().getMultiKeyLoadSizingStrategy(); }
    @Override public org.hibernate.loader.ast.spi.MultiKeyLoadSizingStrategy getBatchLoadSizingStrategy() { return d().getBatchLoadSizingStrategy(); }
    @Override public boolean isJdbcLogWarningsEnabledByDefault() { return d().isJdbcLogWarningsEnabledByDefault(); }
    @Override public void augmentPhysicalTableTypes(java.util.List<java.lang.String> a0) { d().augmentPhysicalTableTypes(a0); }
    @Override public void augmentRecognizedTableTypes(java.util.List<java.lang.String> a0) { d().augmentRecognizedTableTypes(a0); }
    @Override public boolean supportsPartitionBy() { return d().supportsPartitionBy(); }
    @Override public boolean addPartitionKeyToPrimaryKey() { return d().addPartitionKeyToPrimaryKey(); }
    @Override public boolean supportsNamedParameters(java.sql.DatabaseMetaData a0) throws java.sql.SQLException { return d().supportsNamedParameters(a0); }
    @Override public org.hibernate.dialect.NationalizationSupport getNationalizationSupport() { return d().getNationalizationSupport(); }
    @Override public boolean supportsNationalizedMethods() { return d().supportsNationalizedMethods(); }
    @Override public org.hibernate.dialect.aggregate.AggregateSupport getAggregateSupport() { return d().getAggregateSupport(); }
    @Override public boolean supportsUserDefinedTypes() { return d().supportsUserDefinedTypes(); }
    @Override public boolean supportsStandardArrays() { return d().supportsStandardArrays(); }
    @Override public boolean useArrayForMultiValuedParameters() { return d().useArrayForMultiValuedParameters(); }
    @Override public java.lang.String getArrayTypeName(java.lang.String a0, java.lang.String a1, java.lang.Integer a2) { return d().getArrayTypeName(a0, a1, a2); }
    @Override public void appendArrayLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.lang.Object[] a1, org.hibernate.type.descriptor.jdbc.JdbcLiteralFormatter<java.lang.Object> a2, org.hibernate.type.descriptor.WrapperOptions a3) { d().appendArrayLiteral(a0, a1, a2, a3); }
    @Override public boolean supportsDistinctFromPredicate() { return d().supportsDistinctFromPredicate(); }
    @Override public int getPreferredSqlTypeCodeForArray() { return d().getPreferredSqlTypeCodeForArray(); }
    @Override public int getPreferredSqlTypeCodeForBoolean() { return d().getPreferredSqlTypeCodeForBoolean(); }
    @Override public boolean supportsNonQueryWithCTE() { return d().supportsNonQueryWithCTE(); }
    @Override public boolean supportsRecursiveCTE() { return d().supportsRecursiveCTE(); }
    @Override public boolean supportsConflictClauseForInsertCTE() { return d().supportsConflictClauseForInsertCTE(); }
    @Override public boolean supportsValuesList() { return d().supportsValuesList(); }
    @Override public boolean supportsValuesListForInsert() { return d().supportsValuesListForInsert(); }
    @Override public boolean supportsFromClauseInUpdate() { return d().supportsFromClauseInUpdate(); }
    @Override public void appendLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.lang.String a1) { d().appendLiteral(a0, a1); }
    @Override public void appendBinaryLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, byte[] a1) { d().appendBinaryLiteral(a0, a1); }
    @Override public boolean supportsJdbcConnectionLobCreation(java.sql.DatabaseMetaData a0) { return d().supportsJdbcConnectionLobCreation(a0); }
    @Override public boolean supportsMaterializedLobAccess() { return d().supportsMaterializedLobAccess(); }
    @Override public boolean useMaterializedLobWhenCapacityExceeded() { return d().useMaterializedLobWhenCapacityExceeded(); }
    @Override public java.lang.String addSqlHintOrComment(java.lang.String a0, org.hibernate.query.spi.QueryOptions a1, boolean a2) { return d().addSqlHintOrComment(a0, a1, a2); }
    @Override public org.hibernate.query.hql.HqlTranslator getHqlTranslator() { return d().getHqlTranslator(); }
    @Override public org.hibernate.query.sqm.sql.SqmTranslatorFactory getSqmTranslatorFactory() { return d().getSqmTranslatorFactory(); }
    @Override public org.hibernate.sql.ast.SqlAstTranslatorFactory getSqlAstTranslatorFactory() { return d().getSqlAstTranslatorFactory(); }
    @Override public org.hibernate.dialect.SelectItemReferenceStrategy getGroupBySelectItemReferenceStrategy() { return d().getGroupBySelectItemReferenceStrategy(); }
    @Override public org.hibernate.dialect.Dialect.SizeStrategy getSizeStrategy() { return d().getSizeStrategy(); }
    @Override public int getMaxVarcharLength() { return d().getMaxVarcharLength(); }
    @Override public int getMaxNVarcharLength() { return d().getMaxNVarcharLength(); }
    @Override public int getMaxVarbinaryLength() { return d().getMaxVarbinaryLength(); }
    @Override public int getMaxVarcharCapacity() { return d().getMaxVarcharCapacity(); }
    @Override public int getMaxNVarcharCapacity() { return d().getMaxNVarcharCapacity(); }
    @Override public int getMaxVarbinaryCapacity() { return d().getMaxVarbinaryCapacity(); }
    @Override public long getDefaultLobLength() { return d().getDefaultLobLength(); }
    @Override public int getDefaultDecimalPrecision() { return d().getDefaultDecimalPrecision(); }
    @Override public int getDefaultTimestampPrecision() { return d().getDefaultTimestampPrecision(); }
    @Override public int getDefaultIntervalSecondScale() { return d().getDefaultIntervalSecondScale(); }
    @Override public boolean doesRoundTemporalOnOverflow() { return d().doesRoundTemporalOnOverflow(); }
    @Override public int getFloatPrecision() { return d().getFloatPrecision(); }
    @Override public int getDoublePrecision() { return d().getDoublePrecision(); }
    @Override public long getFractionalSecondPrecisionInNanos() { return d().getFractionalSecondPrecisionInNanos(); }
    @Override public boolean supportsBitType() { return d().supportsBitType(); }
    @Override public org.hibernate.dialect.RowLockStrategy getLockRowIdentifier(org.hibernate.LockMode a0) { return d().getLockRowIdentifier(a0); }
    @Override public java.lang.String generatedAs(java.lang.String a0) { return d().generatedAs(a0); }
    @Override public boolean hasDataTypeBeforeGeneratedAs() { return d().hasDataTypeBeforeGeneratedAs(); }
    @Override public org.hibernate.sql.model.MutationOperation createOptionalTableUpdateOperation(org.hibernate.persister.entity.mutation.EntityMutationTarget a0, org.hibernate.sql.model.internal.OptionalTableUpdate a1, org.hibernate.engine.spi.SessionFactoryImplementor a2) { return d().createOptionalTableUpdateOperation(a0, a1, a2); }
    @Override public boolean canDisableConstraints() { return d().canDisableConstraints(); }
    @Override public java.lang.String getDisableConstraintsStatement() { return d().getDisableConstraintsStatement(); }
    @Override public java.lang.String getEnableConstraintsStatement() { return d().getEnableConstraintsStatement(); }
    @Override public java.lang.String getDisableConstraintStatement(java.lang.String a0, java.lang.String a1) { return d().getDisableConstraintStatement(a0, a1); }
    @Override public java.lang.String getEnableConstraintStatement(java.lang.String a0, java.lang.String a1) { return d().getEnableConstraintStatement(a0, a1); }
    @Override public boolean canBatchTruncate() { return d().canBatchTruncate(); }
    @Override public java.lang.String[] getTruncateTableStatements(java.lang.String[] a0) { return d().getTruncateTableStatements(a0); }
    @Override public java.lang.String getTruncateTableStatement(java.lang.String a0) { return d().getTruncateTableStatement(a0); }
    @Override public org.hibernate.sql.ast.spi.ParameterMarkerStrategy getNativeParameterMarkerStrategy() { return d().getNativeParameterMarkerStrategy(); }
    @Override public java.lang.Boolean supportsBatchUpdates() { return d().supportsBatchUpdates(); }
    @Override public java.lang.Boolean supportsRefCursors() { return d().supportsRefCursors(); }
    @Override public java.lang.String getDefaultOrdinalityColumnName() { return d().getDefaultOrdinalityColumnName(); }
    @Override public boolean causesRollback(java.sql.SQLException a0) { return d().causesRollback(a0); }
    @Override public void appendDatetimeFormat(org.hibernate.sql.ast.spi.SqlAppender a0, java.lang.String a1) { d().appendDatetimeFormat(a0, a1); }
    @Override public java.lang.String translateExtractField(org.hibernate.query.common.TemporalUnit a0) { return d().translateExtractField(a0); }
    @Override public java.lang.String translateDurationField(org.hibernate.query.common.TemporalUnit a0) { return d().translateDurationField(a0); }
    @Override public void appendDateTimeLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.time.temporal.TemporalAccessor a1, jakarta.persistence.TemporalType a2, java.util.TimeZone a3) { d().appendDateTimeLiteral(a0, a1, a2, a3); }
    @Override public void appendDateTimeLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.util.Date a1, jakarta.persistence.TemporalType a2, java.util.TimeZone a3) { d().appendDateTimeLiteral(a0, a1, a2, a3); }
    @Override public void appendDateTimeLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.util.Calendar a1, jakarta.persistence.TemporalType a2, java.util.TimeZone a3) { d().appendDateTimeLiteral(a0, a1, a2, a3); }
    @Override public void appendIntervalLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.time.Duration a1) { d().appendIntervalLiteral(a0, a1); }
    @Override public void appendIntervalLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.time.temporal.TemporalAmount a1) { d().appendIntervalLiteral(a0, a1); }
    @Override public void appendUUIDLiteral(org.hibernate.sql.ast.spi.SqlAppender a0, java.util.UUID a1) { d().appendUUIDLiteral(a0, a1); }
    @Override public boolean supportsTemporalLiteralOffset() { return d().supportsTemporalLiteralOffset(); }
    @Override public org.hibernate.dialect.TimeZoneSupport getTimeZoneSupport() { return d().getTimeZoneSupport(); }
    @Override public java.lang.String rowId(java.lang.String a0) { return d().rowId(a0); }
    @Override public int rowIdSqlType() { return d().rowIdSqlType(); }
    @Override public java.lang.String getRowIdColumnString(java.lang.String a0) { return d().getRowIdColumnString(a0); }
    @Override public org.hibernate.dialect.DmlTargetColumnQualifierSupport getDmlTargetColumnQualifierSupport() { return d().getDmlTargetColumnQualifierSupport(); }
    @Override public org.hibernate.dialect.FunctionalDependencyAnalysisSupport getFunctionalDependencyAnalysisSupport() { return d().getFunctionalDependencyAnalysisSupport(); }
    @Override public java.lang.String getCheckConstraintString(org.hibernate.mapping.CheckConstraint a0) { return d().getCheckConstraintString(a0); }
    @Override public java.lang.String appendCheckConstraintOptions(org.hibernate.mapping.CheckConstraint a0, java.lang.String a1) { return d().appendCheckConstraintOptions(a0, a1); }
    @Override public boolean supportsTableOptions() { return d().supportsTableOptions(); }
    @Override public boolean supportsBindingNullSqlTypeForSetNull() { return d().supportsBindingNullSqlTypeForSetNull(); }
    @Override public boolean supportsBindingNullForSetObject() { return d().supportsBindingNullForSetObject(); }
    @Override public boolean supportsFilterClause() { return d().supportsFilterClause(); }
    @Override public boolean supportsRowConstructor() { return d().supportsRowConstructor(); }
    @Override public boolean supportsArrayConstructor() { return d().supportsArrayConstructor(); }
    @Override public boolean supportsDuplicateSelectItemsInQueryGroup() { return d().supportsDuplicateSelectItemsInQueryGroup(); }
    @Override public boolean supportsIntersect() { return d().supportsIntersect(); }
    @Override public boolean supportsJoinInMutationStatementSubquery() { return d().supportsJoinInMutationStatementSubquery(); }
    @Override public boolean supportsJoinsInDelete() { return d().supportsJoinsInDelete(); }
    @Override public boolean supportsNestedSubqueryCorrelation() { return d().supportsNestedSubqueryCorrelation(); }
    @Override public boolean supportsRecursiveCycleClause() { return d().supportsRecursiveCycleClause(); }
    @Override public boolean supportsRecursiveCycleUsingClause() { return d().supportsRecursiveCycleUsingClause(); }
    @Override public boolean supportsRecursiveSearchClause() { return d().supportsRecursiveSearchClause(); }
    @Override public boolean supportsSimpleQueryGrouping() { return d().supportsSimpleQueryGrouping(); }
    @Override public boolean supportsCrossJoin() { return d().supportsCrossJoin(); }
    @Override public boolean supportsRowValueConstructorSyntax() { return d().supportsRowValueConstructorSyntax(); }
    @Override public boolean supportsRowValueConstructorGtLtSyntax() { return d().supportsRowValueConstructorGtLtSyntax(); }
    @Override public boolean supportsRowValueConstructorDistinctFromSyntax() { return d().supportsRowValueConstructorDistinctFromSyntax(); }
    @Override public boolean supportsWithClause() { return d().supportsWithClause(); }
    @Override public boolean supportsWithClauseInSubquery() { return d().supportsWithClauseInSubquery(); }
    @Override public boolean supportsNestedWithClause() { return d().supportsNestedWithClause(); }
    @Override public boolean supportsRowValueConstructorSyntaxInQuantifiedPredicates() { return d().supportsRowValueConstructorSyntaxInQuantifiedPredicates(); }
    @Override public boolean supportsRowValueConstructorSyntaxInInList() { return d().supportsRowValueConstructorSyntaxInInList(); }
    @Override public boolean supportsRowValueConstructorSyntaxInInSubQuery() { return d().supportsRowValueConstructorSyntaxInInSubQuery(); }
    @Override public boolean supportsUniqueConstraints() { return d().supportsUniqueConstraints(); }
    @Override public boolean supportsCteHeaderColumnList() { return d().supportsCteHeaderColumnList(); }
}
