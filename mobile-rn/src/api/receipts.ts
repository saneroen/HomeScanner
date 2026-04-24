import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from './client';
import { ENV } from '../config/env';

export type ReceiptStatus = 'CAPTURED_RAW' | 'PROCESSING' | 'NEEDS_REVIEW' | 'CONFIRMED' | 'FAILED';

export type ReceiptItem = {
  id: string;
  receipt_id: string;
  name: string;
  qty: number;
  unit_price: number;
  line_total: number;
  deleted: number;
  created_at: string;
  updated_at: string;
};

export type Receipt = {
  id: string;
  title: string;
  status: ReceiptStatus;
  image_uri: string | null;
  total: number | null;
  created_at: string;
  updated_at: string;
  items: ReceiptItem[];
};

export type ReceiptFieldConfidence = {
  name: number;
  qty: number;
  unit_price: number;
  line_total: number;
};

export type ReceiptExtractedItem = {
  name: string;
  qty: number;
  unit_price: number;
  line_total: number;
  confidence: ReceiptFieldConfidence;
};

export type ReceiptClassificationResult = {
  is_receipt: boolean;
  confidence: number;
  reason_codes: string[];
};

export type ReceiptExtractionResult = {
  merchant: string | null;
  date: string | null;
  subtotal: number | null;
  tax: number | null;
  total: number | null;
  items: ReceiptExtractedItem[];
  sensitive: Record<string, string>;
};

export type ReceiptClassifyPayload = {
  force?: boolean;
  model_version: string;
  latency_ms: number;
  classification: ReceiptClassificationResult;
  extraction: ReceiptExtractionResult;
};

export type ReceiptClassifyResponse = {
  receipt_id: string;
  classification: ReceiptClassificationResult;
  extraction: ReceiptExtractionResult;
  redaction: {
    policy: string;
    secret_fields_count: number;
    masked_fields: string[];
  };
  validator: {
    accepted: boolean;
    issues: string[];
  };
  status: ReceiptStatus;
};

const receiptsKey = ['receipts'];

export const useReceiptsQuery = () =>
  useQuery({
    queryKey: receiptsKey,
    queryFn: async () => {
      const response = await api.get<Receipt[]>('/receipts');
      return response.data;
    },
  });

export const useReceiptQuery = (receiptId: string | null) =>
  useQuery({
    queryKey: ['receipt', receiptId],
    enabled: !!receiptId,
    queryFn: async () => {
      const response = await api.get<Receipt>(`/receipts/${receiptId}`);
      return response.data;
    },
  });

export const useCreateReceiptMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (title?: string) => {
      const response = await api.post<Receipt>('/receipts', {
        title: title || `Receipt ${new Date().toLocaleString()}`,
        status: 'CAPTURED_RAW',
      });
      return response.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: receiptsKey }),
  });
};

export const useUpdateReceiptMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      receiptId: string;
      title?: string;
      status?: ReceiptStatus;
      imageUri?: string | null;
      total?: number | null;
    }) => {
      const response = await api.patch<Receipt>(`/receipts/${payload.receiptId}`, {
        title: payload.title,
        status: payload.status,
        image_uri: payload.imageUri,
        total: payload.total,
      });
      return response.data;
    },
    onSuccess: (receipt) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', receipt.id] });
    },
  });
};

export const useDeleteReceiptMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (receiptId: string) => {
      await api.delete(`/receipts/${receiptId}`);
      return receiptId;
    },
    onSuccess: (receiptId) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', receiptId] });
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
    },
  });
};

export const useAddReceiptItemMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { receiptId: string; name: string; qty: number; unitPrice: number }) => {
      const lineTotal = payload.qty * payload.unitPrice;
      const response = await api.post<ReceiptItem>(`/receipts/${payload.receiptId}/items`, {
        name: payload.name,
        qty: payload.qty,
        unit_price: payload.unitPrice,
        line_total: lineTotal,
      });
      return response.data;
    },
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', vars.receiptId] });
    },
  });
};

export const useDeleteReceiptItemMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { receiptId: string; itemId: string }) => {
      await api.delete(`/receipts/${payload.receiptId}/items/${payload.itemId}`);
      return payload;
    },
    onSuccess: ({ receiptId }) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', receiptId] });
    },
  });
};

export const useUpdateReceiptItemMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      receiptId: string;
      itemId: string;
      name?: string;
      qty?: number;
      unitPrice?: number;
      lineTotal?: number;
      deleted?: number;
    }) => {
      const response = await api.patch<ReceiptItem>(`/receipts/${payload.receiptId}/items/${payload.itemId}`, {
        name: payload.name,
        qty: payload.qty,
        unit_price: payload.unitPrice,
        line_total: payload.lineTotal,
        deleted: payload.deleted,
      });
      return response.data;
    },
    onSuccess: (item) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', item.receipt_id] });
    },
  });
};

export const useConfirmReceiptMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (receiptId: string) => {
      const response = await api.post<Receipt>(`/receipts/${receiptId}/confirm`);
      return response.data;
    },
    onSuccess: (receipt) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', receipt.id] });
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
    },
  });
};

export const useUploadReceiptImageMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { receiptId: string; uri: string; fileName?: string; mimeType?: string }) => {
      const formData = new FormData();
      formData.append('image', {
        uri: payload.uri,
        name: payload.fileName ?? 'receipt.jpg',
        type: payload.mimeType ?? 'image/jpeg',
      } as unknown as Blob);

      const response = await fetch(`${ENV.API_BASE_URL.replace(/\/$/, '')}/receipts/${payload.receiptId}/image`, {
        method: 'POST',
        body: formData,
      });
      if (!response.ok) {
        const detail = await response.text();
        throw new Error(`upload failed (${response.status}): ${detail}`);
      }
      return (await response.json()) as Receipt;
    },
    onSuccess: (receipt) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', receipt.id] });
    },
  });
};

export const useClassifyReceiptMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { receiptId: string; request?: ReceiptClassifyPayload }) => {
      const response = await api.post<ReceiptClassifyResponse>(`/receipts/${payload.receiptId}/classify`, payload.request);
      return response.data;
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: receiptsKey });
      queryClient.invalidateQueries({ queryKey: ['receipt', result.receipt_id] });
      queryClient.invalidateQueries({ queryKey: ['receipt-classification', result.receipt_id] });
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
    },
  });
};
