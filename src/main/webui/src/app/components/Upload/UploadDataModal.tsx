import {
  Button,
  ComposedModal,
  FileUploaderDropContainer,
  FileUploaderItem,
  InlineLoading,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  TextArea,
  TextInput,
} from '@carbon/react';
import type { UploadData } from '@client/types.gen.ts';
import { getDashboardSummariesQueryKey, getLabelValuesQueryKey, uploadMutation } from '@client/@tanstack/react-query.gen.ts';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useNotification } from '@app/context/useNotification.tsx';
import './UploadDataModal.css';

interface UploadDataModalProps {
  open: boolean;
  onClose: () => void;
  folderId: number;
  onUploadSuccess: (fileName: string, uploadId: number) => void;
}

const TAB_FILE = 0;
const TAB_FOLDER = 1;
const TAB_PASTE = 2;
const TAB_URL = 3;

type FileStatus = 'pending' | 'success' | 'error';

export const UploadDataModal = ({ open, onClose, folderId, onUploadSuccess }: UploadDataModalProps) => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState(TAB_FILE);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number | null>(null);
  const [currentStatus, setCurrentStatus] = useState<FileStatus | null>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const [pasteText, setPasteText] = useState('');
  const [pasteError, setPasteError] = useState<string | null>(null);
  const [urlInput, setUrlInput] = useState('');
  const [urlError, setUrlError] = useState<string | null>(null);

  const notifications = useNotification();
  const upload = useMutation(uploadMutation());

  const isUploading = currentStatus === 'pending';

  function addFiles(incoming: File[]) {
    const jsonFiles = incoming.filter((f) => f.name.endsWith('.json'));
    if (jsonFiles.length === 0) {
      notifications.warning('No .json files found.');
      return;
    }
    setSelectedFiles((prev) => {
      const existingNames = new Set(prev.map((f) => f.name));
      const newFiles = jsonFiles.filter((f) => !existingNames.has(f.name));
      return [...prev, ...newFiles];
    });
  }

  function handleClose() {
    setActiveTab(TAB_FILE);
    setSelectedFiles([]);
    setCurrentIndex(null);
    setCurrentStatus(null);
    setPasteText('');
    setPasteError(null);
    setUrlInput('');
    setUrlError(null);
    upload.reset();
    onClose();
  }

  async function handleUpload() {
    const items: Array<{ label: string; body: UploadData['body'] }> = [];

    if (activeTab === TAB_FILE || activeTab === TAB_FOLDER) {
      for (const file of selectedFiles) items.push({ label: file.name, body: { file } });
    } else if (activeTab === TAB_PASTE && pasteError === null) {
      const label = pasteText.trim().substring(0, 50) + (pasteText.trim().length >= 50 ? '...' : '');
      items.push({ label, body: { raw: pasteText.trim() } });
    } else if (activeTab === TAB_URL) {
      items.push({ label: urlInput.trim(), body: { url: urlInput.trim() } });
    }

    if (items.length === 0) return;

    let successCount = 0;

    for (let i = 0; i < items.length; i++) {
      const item = items[i]!;
      setCurrentIndex(i);
      setCurrentStatus('pending');

      try {
        const uploadId = await upload.mutateAsync({ path: { id: folderId }, body: item.body });
        void queryClient.invalidateQueries({ queryKey: getDashboardSummariesQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getLabelValuesQueryKey({ path: { id: folderId } }) });
        void queryClient.invalidateQueries({
          predicate: (query) => {
            const key = query.queryKey[0] as { _id?: string; path?: { folderId?: number } } | undefined;
            return key?._id === 'getViewData' && key.path?.folderId === folderId;
          },
        });
        onUploadSuccess(item.label, uploadId);
        setCurrentStatus('success');
        successCount++;
      } catch (e: unknown) {
        setCurrentStatus('error');
        notifications.handleError(item.label, e);
      }
    }

    if (successCount > 0) {
      notifications.success(
        items.length > 1
          ? `${successCount} file${successCount > 1 ? 's' : ''} uploaded successfully`
          : `'${items[0]!.label}' uploaded successfully`
      );
      setTimeout(handleClose, 500);
    }
  }

  function fileUpload() {
    if (selectedFiles.length === 0) return null;
    return (
      <div className="upload-file-list">
        <p className="upload-file-list__count">
          {selectedFiles.length} file{selectedFiles.length > 1 ? 's' : ''} selected
        </p>
        {selectedFiles.map((file, idx) => (
          <FileUploaderItem
            key={`${file.name}-${idx}`}
            name={file.name}
            status={
              idx === currentIndex
                ? currentStatus === 'pending' ? 'uploading' : 'complete'
                : idx < (currentIndex ?? -1) ? 'complete' : 'edit'
            }
            invalid={idx === currentIndex && currentStatus === 'error'}
            errorSubject="Upload failed"
            onDelete={() => setSelectedFiles((prev) => prev.filter((_, i) => i !== idx))}
          />
        ))}
      </div>
    );
  }

  const canUpload =
    !isUploading &&
    !upload.isPending &&
    (activeTab === TAB_FILE || activeTab === TAB_FOLDER
      ? selectedFiles.length > 0 && currentIndex === null
      : activeTab === TAB_PASTE
        ? pasteText.trim().length > 0 && pasteError === null
        : urlInput.trim().length > 0 && urlError === null);

  return (
    <ComposedModal open={open} onClose={handleClose} size="md">
      <ModalHeader title="Upload data" />
      <ModalBody>
        <Tabs selectedIndex={activeTab} onChange={({ selectedIndex }) => {
          setActiveTab(selectedIndex);
        }}>
          <TabList aria-label="Upload mode">
            <Tab>File Upload</Tab>
            <Tab>Folder Upload</Tab>
            <Tab>Paste JSON</Tab>
            <Tab>URL</Tab>
          </TabList>
          <TabPanels>

            <TabPanel>
              <div className="upload-drop-zone">
                <FileUploaderDropContainer
                  accept={['.json', 'application/json']}
                  labelText="Drag and drop files here or click to upload (.json only)"
                  multiple
                  disabled={isUploading}
                  onAddFiles={(_e, { addedFiles }) => addFiles(addedFiles)}
                />
              </div>
              {fileUpload()}
            </TabPanel>

            <TabPanel>
              <div className="upload-drop-zone">
                <Button
                  kind="tertiary"
                  disabled={isUploading}
                  onClick={() => folderInputRef.current?.click()}
                >
                  Choose a folder (all .json files inside)
                </Button>
                <input
                  ref={folderInputRef}
                  type="file"
                  // @ts-expect-error — webkitdirectory is not in React's HTMLInputElement types
                  webkitdirectory=""
                  hidden
                  onChange={(e) => { addFiles(Array.from(e.target.files ?? [])); e.target.value = ''; }}
                />
              </div>
              {fileUpload()}
            </TabPanel>

            <TabPanel>
              <TextArea
                id="paste-json"
                className="upload-paste-json"
                labelText="Paste JSON content"
                placeholder='{ "key": "value" }'
                value={pasteText}
                onChange={(e) => {
                  const val = e.target.value;
                  setPasteText(val);
                  try { JSON.parse(val); setPasteError(null); }
                  catch { setPasteError('Invalid JSON — Please check the pasted content'); }
                }}
                rows={10}
                invalid={pasteError !== null}
                invalidText={pasteError ?? ''}
              />
            </TabPanel>

            <TabPanel>
              <TextInput
                id="url-input"
                labelText="URL"
                placeholder="https://example.com/data.json"
                value={urlInput}
                onChange={(e) => {
                  const val = e.target.value;
                  setUrlInput(val);
                  if (val.trim().length === 0) { setUrlError(null); }
                  else if (!val.trim().startsWith('http://') && !val.trim().startsWith('https://')) {
                    setUrlError('URL must start with http:// or https://');
                  } else { setUrlError(null); }
                }}
                invalid={urlError !== null}
                invalidText={urlError ?? ''}
              />
            </TabPanel>

          </TabPanels>
        </Tabs>

      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={handleClose}>
          {currentIndex !== null && !isUploading ? 'Close' : 'Cancel'}
        </Button>
        <Button
          kind="primary"
          onClick={() => { void handleUpload(); }}
          disabled={!canUpload}
        >
          {isUploading || upload.isPending
            ? <InlineLoading description="Uploading…" status="active" />
            : 'Upload'}
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
};
