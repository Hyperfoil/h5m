import {
  Button,
  ComposedModal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextInput,
} from '@carbon/react';
import { createFolderMutation } from '@client/@tanstack/react-query.gen.ts';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { AxiosError } from 'axios';

interface CreateFolderModalProps {
  open: boolean;
  onClose: () => void;
}

export const CreateFolderModal = ({ open, onClose }: CreateFolderModalProps) => {
  const [folderName, setFolderName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const createFolder = useMutation({
    ...createFolderMutation(),
    onSuccess: () => {
      void queryClient.invalidateQueries();
      setFolderName('');
      setError(null);
      onClose();
    },
    onError: (e: AxiosError<Error>) => {
      if (e.response?.status === 409) {
        setError('A folder with same name already exists');
      } else {
        setError(e.message ?? 'Failed to create folder');
      }
    },
  });

  const handleSave = () => {
    if (folderName.trim() === '') {
      setError('Folder name cannot be empty');
      return;
    }
    createFolder.mutate({ path: { name: folderName.trim() } });
  };

  return (
    <ComposedModal open={open} onClose={() => { setFolderName(''); setError(null); onClose(); }}>
      <ModalHeader title="Create Folder" />
      <ModalBody>
        <TextInput
          id="folder-name"
          labelText="Folder name"
          placeholder="e.g. benchmarks"
          value={folderName}
          onChange={(e) => setFolderName(e.target.value)}
        />
        {error && (
          <p style={{ color: 'var(--cds-support-error)', marginTop: '0.5rem' }}>
            {error}
          </p>
        )}
      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={() => { setFolderName(''); setError(null); onClose(); }}>
          Cancel
        </Button>
        <Button
          kind="primary"
          onClick={handleSave}
          disabled={createFolder.isPending}
        >
          {createFolder.isPending ? 'Saving...' : 'Save'}
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
};
