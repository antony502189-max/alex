import { create } from "zustand";

export type AttachmentTransferDirection = "UPLOAD" | "DOWNLOAD";
export type AttachmentTransferStatus =
  | "RUNNING"
  | "PAUSED"
  | "FAILED"
  | "COMPLETED";

export type AttachmentTransferState = {
  attachmentId: string;
  direction: AttachmentTransferDirection;
  status: AttachmentTransferStatus;
  progress: number;
  transferredBytes: number;
  totalBytes: number | null;
  sessionId: string | null;
  localUri: string | null;
  error: string | null;
  updatedAt: string;
};

type State = {
  transfers: Record<string, AttachmentTransferState>;
  upsertTransfer: (
    attachmentId: string,
    transfer: Omit<AttachmentTransferState, "attachmentId" | "updatedAt"> &
      Partial<Pick<AttachmentTransferState, "updatedAt">>
  ) => void;
  patchTransfer: (
    attachmentId: string,
    patch: Partial<Omit<AttachmentTransferState, "attachmentId" | "direction">>
  ) => void;
  clearTransfer: (attachmentId: string) => void;
};

export const useAttachmentTransferStore = create<State>((set) => ({
  transfers: {},
  upsertTransfer: (attachmentId, transfer) =>
    set((state) => ({
      transfers: {
        ...state.transfers,
        [attachmentId]: {
          attachmentId,
          updatedAt: transfer.updatedAt ?? new Date().toISOString(),
          ...transfer
        }
      }
    })),
  patchTransfer: (attachmentId, patch) =>
    set((state) => {
      const current = state.transfers[attachmentId];
      if (!current) {
        return state;
      }
      return {
        transfers: {
          ...state.transfers,
          [attachmentId]: {
            ...current,
            ...patch,
            updatedAt: new Date().toISOString()
          }
        }
      };
    }),
  clearTransfer: (attachmentId) =>
    set((state) => {
      const nextTransfers = { ...state.transfers };
      delete nextTransfers[attachmentId];
      return { transfers: nextTransfers };
    })
}));
