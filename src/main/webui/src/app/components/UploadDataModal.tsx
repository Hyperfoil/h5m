import type { JqValue } from '@client/types.gen.ts';

import {
  Button,
  ComposedModal,
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
import { Upload } from '@carbon/icons-react';
import { uploadMutation } from '@client/@tanstack/react-query.gen.ts';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { AxiosError } from 'axios';
import { useNotification } from '@app/context/useNotification.tsx';

interface UploadDataModalProps {
  open: boolean;
  onClose: () => void;
  folderId: number;
  onUploadSuccess: (fileName: string, uploadId: number) => void;
}

const TAB_FILE = 0;
const TAB_PASTE = 1;
const TAB_URL = 2;

type FileStatus = 'pending' | 'success' | 'error';

async function parseJsonFile(file: File): Promise<JqValue> {
  const text = await file.text();
  try {
    return JSON.parse(text) as JqValue;
  } catch {
    throw new Error(`${file.name}: invalid JSON`);
  }
}

export const UploadDataModal = ({ open, onClose, folderId, onUploadSuccess }: UploadDataModalProps) => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState(TAB_FILE);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number | null>(null);
  const [currentStatus, setCurrentStatus] = useState<FileStatus | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const [pasteName, setPasteName] = useState('');
  const [pasteText, setPasteText] = useState('');
  const [urlInput, setUrlInput] = useState('');

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
    setIsDragging(false);
    setPasteName('');
    setPasteText('');
    setUrlInput('');
    upload.reset();
    onClose();
  }

  function extractMsg(e: AxiosError<Error>): string {
    const msg = e.response?.data as string | undefined;
    return typeof msg === 'string' && msg.length > 0 ? msg : (e.message ?? 'Upload failed');
  }

  async function handleUpload() {
    if (activeTab === TAB_FILE) {
      if (selectedFiles.length === 0) return;

      let successCount = 0;
      for (let i = 0; i < selectedFiles.length; i++) {
        const file = selectedFiles[i]!;
        setCurrentIndex(i);
        setCurrentStatus('pending');

        let data: JqValue;
        try {
          data = await parseJsonFile(file);
        } catch (e) {
          setCurrentStatus('error');
          notifications.error(file.name, e instanceof Error ? e.message : 'Failed to read file');
          continue;
        }

        await new Promise<void>((resolve) => {
          upload.mutate(
            { path: { id: folderId }, body: { file: data } },
            {
              onSuccess: (uploadId: number) => {
                void queryClient.invalidateQueries();
                onUploadSuccess(file.name, uploadId);
                setCurrentStatus('success');
                successCount++;
                setTimeout(handleClose, 500);
                resolve();
              },
              onError: (e: AxiosError<Error>) => {
                setCurrentStatus('error');
                notifications.error(file.name, extractMsg(e));
                resolve();
              },
            }
          );
        });
      }

      if (successCount > 0) {
        notifications.success(`${successCount} file${successCount > 1 ? 's' : ''} uploaded successfully`);
      }

    } else if (activeTab === TAB_PASTE) {
      let parsed: unknown;
      try {
        parsed = JSON.parse(pasteText);
      } catch {
        notifications.error('Invalid JSON', 'Please check the pasted content.');
        return;
      }
      upload.mutate(
        { path: { id: folderId }, body: { file: parsed as JqValue } },
        {
          onSuccess: (uploadId: number) => {
            void queryClient.invalidateQueries();
            onUploadSuccess(pasteName.trim(), uploadId);
            notifications.success(`'${pasteName.trim()}' uploaded successfully`);
            handleClose();
          },
          onError: (e: AxiosError<Error>) => {
            notifications.error('Upload failed', extractMsg(e));
          },
        }
      );

    } else if (activeTab === TAB_URL) {
      upload.mutate(
        { path: { id: folderId }, body: { url: urlInput.trim() } },
        {
          onSuccess: (uploadId: number) => {
            void queryClient.invalidateQueries();
            onUploadSuccess(urlInput.trim(), uploadId);
            notifications.success('URL uploaded successfully');
            handleClose();
          },
          onError: (e: AxiosError<Error>) => {
            notifications.error('Upload failed', extractMsg(e));
          },
        }
      );
    }
  }

  const canUpload =
    !isUploading &&
    !upload.isPending &&
    (activeTab === TAB_FILE
      ? selectedFiles.length > 0 && currentIndex === null
      : activeTab === TAB_PASTE
        ? pasteText.trim().length > 0 && pasteName.trim().length > 0
        : urlInput.trim().length > 0);

  function statusLabel(status: FileStatus) {
    if (status === 'pending') return <span style={{ fontSize: '0.75rem', color: 'var(--cds-text-placeholder)' }}>Pending</span>;
    if (status === 'success') return <span style={{ fontSize: '0.75rem', color: 'var(--cds-support-success)', fontWeight: 600 }}>Success</span>;
    return <span style={{ fontSize: '0.75rem', color: 'var(--cds-support-error)', fontWeight: 600 }}>Error</span>;
  }

  return (
    <ComposedModal open={open} onClose={handleClose} size="md">
      <ModalHeader title="Upload data" />
      <ModalBody>
        <Tabs selectedIndex={activeTab} onChange={({ selectedIndex }) => {
          setActiveTab(selectedIndex);
        }}>
          <TabList aria-label="Upload mode">
            <Tab>File Upload</Tab>
            <Tab>Paste JSON</Tab>
            <Tab>URL</Tab>
          </TabList>
          <TabPanels>

            <TabPanel>
              {currentIndex !== null ? (
                <div style={{
                  maxHeight: '260px',
                  overflowY: 'auto',
                  border: '1px solid var(--cds-border-subtle-01)',
                  borderRadius: '4px',
                }}>
                  {selectedFiles.map((file, idx) => (
                    <div
                      key={`${file.name}-${idx}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                        padding: 'var(--cds-spacing-03) var(--cds-spacing-04)',
                        background: idx % 2 === 0 ? 'var(--cds-layer-02)' : 'var(--cds-layer-01)',
                        borderBottom: idx < selectedFiles.length - 1 ? '1px solid var(--cds-border-subtle-01)' : 'none',
                      }}
                    >
                      <span style={{ flex: 1, fontSize: '0.875rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {file.name}
                      </span>
                      {idx === currentIndex && (
                        currentStatus === 'pending'
                          ? <InlineLoading status="active" style={{ width: 'auto' }} />
                          : statusLabel(currentStatus!)
                      )}
                      {idx < currentIndex && statusLabel('success')}
                    </div>
                  ))}
                </div>
              ) : (
                <>
                  <div
                    style={{
                      border: `2px dashed ${isDragging ? 'var(--cds-interactive)' : 'var(--cds-border-strong-01)'}`,
                      borderRadius: '4px',
                      padding: 'var(--cds-spacing-08)',
                      textAlign: 'center',
                      background: isDragging ? 'var(--cds-layer-hover)' : 'var(--cds-layer-02)',
                      cursor: 'pointer',
                    }}
                    onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
                    onDragLeave={() => setIsDragging(false)}
                    onDrop={(e) => {
                      e.preventDefault();
                      setIsDragging(false);
                      addFiles(Array.from(e.dataTransfer.files));
                    }}
                    onClick={() => fileInputRef.current?.click()}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === 'Enter' && fileInputRef.current?.click()}
                    aria-label="Drop JSON files or a folder here, or click to browse"
                  >
                    <Upload size={24} style={{ marginBottom: 'var(--cds-spacing-03)', opacity: 0.6 }} />
                    <p style={{ margin: 0, fontSize: '0.875rem' }}>
                      <span style={{ color: 'var(--cds-link-primary)', textDecoration: 'underline' }}>
                        Click to upload files
                      </span>
                      {' or '}
                      <span
                        style={{ color: 'var(--cds-link-primary)', textDecoration: 'underline', cursor: 'pointer' }}
                        onClick={(e) => { e.stopPropagation(); folderInputRef.current?.click(); }}
                      >
                        upload a folder
                      </span>
                      {', or drag and drop'}
                    </p>
                  </div>

                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json,application/json"
                    multiple
                    style={{ display: 'none' }}
                    onChange={(e) => {
                      addFiles(Array.from(e.target.files ?? []));
                      e.target.value = '';
                    }}
                  />

                  <input
                    ref={folderInputRef}
                    type="file"
                    // @ts-expect-error — webkitdirectory is not in React's HTMLInputElement types
                    webkitdirectory=""
                    style={{ display: 'none' }}
                    onChange={(e) => {
                      addFiles(Array.from(e.target.files ?? []));
                      e.target.value = '';
                    }}
                  />

                  {selectedFiles.length > 0 && (
                    <div style={{
                      marginTop: 'var(--cds-spacing-04)',
                      maxHeight: '200px',
                      overflowY: 'auto',
                      border: '1px solid var(--cds-border-subtle-01)',
                      borderRadius: '4px',
                    }}>
                      {selectedFiles.map((file, idx) => (
                        <div
                          key={`${file.name}-${idx}`}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 'var(--cds-spacing-03)',
                            padding: 'var(--cds-spacing-03) var(--cds-spacing-04)',
                            background: idx % 2 === 0 ? 'var(--cds-layer-02)' : 'var(--cds-layer-01)',
                            borderBottom: idx < selectedFiles.length - 1 ? '1px solid var(--cds-border-subtle-01)' : 'none',
                          }}
                        >
                          <span style={{ flex: 1, fontSize: '0.875rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {file.name}
                          </span>
                          <span style={{ fontSize: '0.75rem', opacity: 0.6, whiteSpace: 'nowrap' }}>
                            {(file.size / 1024).toFixed(1)} KB
                          </span>
                          <button
                            type="button"
                            style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', opacity: 0.7, fontSize: '0.875rem' }}
                            onClick={() => setSelectedFiles((prev) => prev.filter((_, i) => i !== idx))}
                            aria-label={`Remove ${file.name}`}
                          >
                            ✕
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                  {selectedFiles.length > 1 && (
                    <p style={{ margin: 'var(--cds-spacing-02) 0 0', fontSize: '0.75rem', opacity: 0.6 }}>
                      {selectedFiles.length} files selected
                    </p>
                  )}
                </>
              )}
            </TabPanel>

            <TabPanel>
              <TextInput
                id="paste-name"
                labelText="Name"
                placeholder="e.g. test-run-01"
                value={pasteName}
                onChange={(e) => setPasteName(e.target.value)}
                style={{ marginBottom: 'var(--cds-spacing-05)' }}
              />
              <TextArea
                id="paste-json"
                labelText="Paste JSON content"
                placeholder='{ "key": "value" }'
                value={pasteText}
                onChange={(e) => { setPasteText(e.target.value); }}
                rows={10}
                style={{ fontFamily: 'monospace', fontSize: '0.8125rem' }}
              />
            </TabPanel>

            <TabPanel>
              <TextInput
                id="url-input"
                labelText="URL"
                placeholder="https://example.com/data.json"
                value={urlInput}
                onChange={(e) => { setUrlInput(e.target.value); }}
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
