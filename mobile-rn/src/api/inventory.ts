import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from './client';

export type InventoryItem = {
  id: string;
  normalized_key: string;
  display_name: string;
  category: string;
  purchase_count: number;
  last_purchase_price: number | null;
  last_purchased_at: string | null;
  created_at: string;
  updated_at: string;
};

export type InventoryEvent = {
  id: string;
  item_id: string;
  receipt_id: string | null;
  receipt_item_id: string | null;
  quantity: number;
  unit_price: number;
  line_total: number;
  occurred_at: string;
};

export const useInventoryItemsQuery = () =>
  useQuery({
    queryKey: ['inventory-items'],
    queryFn: async () => {
      const response = await api.get<InventoryItem[]>('/inventory-items');
      return response.data;
    },
  });

export const useInventoryItemQuery = (itemId: string | null) =>
  useQuery({
    queryKey: ['inventory-item', itemId],
    enabled: !!itemId,
    queryFn: async () => {
      const response = await api.get<InventoryItem>(`/inventory-items/${itemId}`);
      return response.data;
    },
  });

export const useInventoryEventsQuery = (itemId: string | null) =>
  useQuery({
    queryKey: ['inventory-events', itemId],
    enabled: !!itemId,
    queryFn: async () => {
      const response = await api.get<InventoryEvent[]>(`/inventory-items/${itemId}/events`);
      return response.data;
    },
  });

export const useDeleteInventoryItemMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (itemId: string) => {
      await api.delete(`/inventory-items/${itemId}`);
      return itemId;
    },
    onSuccess: (itemId) => {
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
      queryClient.invalidateQueries({ queryKey: ['inventory-item', itemId] });
      queryClient.invalidateQueries({ queryKey: ['inventory-events', itemId] });
    },
  });
};
