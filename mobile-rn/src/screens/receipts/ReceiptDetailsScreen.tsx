import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  ReceiptItem,
  useAddReceiptItemMutation,
  useConfirmReceiptMutation,
  useDeleteReceiptMutation,
  useDeleteReceiptItemMutation,
  useReceiptQuery,
  useUpdateReceiptItemMutation,
  useUpdateReceiptMutation,
} from '../../api/receipts';
import { ReceiptsStackParamList } from '../../navigation/types';

type Props = NativeStackScreenProps<ReceiptsStackParamList, 'ReceiptDetails'>;
type TabKey = 'SCAN' | 'ITEMS' | 'JSON';

function toNumber(value: string, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function EditableItemRow({
  item,
  receiptId,
}: {
  item: ReceiptItem;
  receiptId: string;
}) {
  const [name, setName] = useState(item.name);
  const [qty, setQty] = useState(String(item.qty));
  const [unitPrice, setUnitPrice] = useState(String(item.unit_price));

  const updateItem = useUpdateReceiptItemMutation();
  const deleteItem = useDeleteReceiptItemMutation();

  return (
    <View style={styles.itemCard}>
      <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="Item name" />
      <View style={styles.row}>
        <TextInput
          style={[styles.input, styles.smallInput]}
          value={qty}
          onChangeText={setQty}
          keyboardType="numeric"
          placeholder="Qty"
        />
        <TextInput
          style={[styles.input, styles.smallInput]}
          value={unitPrice}
          onChangeText={setUnitPrice}
          keyboardType="numeric"
          placeholder="Unit price"
        />
      </View>

      <View style={styles.row}>
        <Pressable
          style={styles.actionButton}
          onPress={async () => {
            const safeQty = Math.max(0.01, toNumber(qty, item.qty));
            const safeUnitPrice = Math.max(0, toNumber(unitPrice, item.unit_price));
            await updateItem.mutateAsync({
              receiptId,
              itemId: item.id,
              name: name.trim() || item.name,
              qty: safeQty,
              unitPrice: safeUnitPrice,
              lineTotal: safeQty * safeUnitPrice,
            });
          }}
        >
          <Text style={styles.actionButtonText}>Save</Text>
        </Pressable>
        <Pressable
          style={[styles.actionButton, styles.deleteButton]}
          onPress={async () => {
            await deleteItem.mutateAsync({ receiptId, itemId: item.id });
          }}
        >
          <Text style={[styles.actionButtonText, styles.deleteButtonText]}>Delete</Text>
        </Pressable>
      </View>
    </View>
  );
}

export function ReceiptDetailsScreen({ navigation, route }: Props) {
  const { receiptId } = route.params;
  const [tab, setTab] = useState<TabKey>('ITEMS');

  const [newName, setNewName] = useState('');
  const [newQty, setNewQty] = useState('1');
  const [newUnitPrice, setNewUnitPrice] = useState('0');
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleInput, setTitleInput] = useState('');

  const receiptQuery = useReceiptQuery(receiptId);
  const addItem = useAddReceiptItemMutation();
  const updateReceipt = useUpdateReceiptMutation();
  const deleteReceipt = useDeleteReceiptMutation();
  const confirmReceipt = useConfirmReceiptMutation();

  const receipt = receiptQuery.data;

  const activeItems = useMemo(
    () => (receipt?.items ?? []).filter((item) => item.deleted === 0),
    [receipt?.items],
  );

  if (receiptQuery.isLoading) {
    return (
      <View style={styles.loaderWrap}>
        <ActivityIndicator size="small" />
      </View>
    );
  }

  if (!receipt) {
    return (
      <View style={styles.loaderWrap}>
        <Text style={styles.emptyText}>Receipt not found.</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
    <View style={styles.container}>
      <View style={styles.navRow}>
        <Pressable style={styles.backButton} onPress={() => navigation.goBack()}>
          <Ionicons name="chevron-back" size={16} color="#0f172a" />
          <Text style={styles.backText}>Back</Text>
        </Pressable>
      </View>
      <View style={styles.headerCard}>
      <View style={styles.header}>
        {editingTitle ? (
          <TextInput
            style={styles.titleInput}
            value={titleInput}
            onChangeText={setTitleInput}
            placeholder="Receipt title"
          />
        ) : (
          <Text style={styles.title}>{receipt.title}</Text>
        )}
        <Text style={styles.meta}>Status: {receipt.status}</Text>
        <Text style={styles.meta}>Total: {receipt.total != null ? `$${receipt.total.toFixed(2)}` : 'N/A'}</Text>
        <View style={styles.row}>
          {editingTitle ? (
            <>
              <Pressable
                style={styles.actionButton}
                onPress={async () => {
                  const safeTitle = titleInput.trim();
                  if (!safeTitle) return;
                  await updateReceipt.mutateAsync({ receiptId, title: safeTitle });
                  setEditingTitle(false);
                }}
              >
                <Text style={styles.actionButtonText}>Save Title</Text>
              </Pressable>
              <Pressable
                style={[styles.actionButton, styles.deleteButton]}
                onPress={() => {
                  setEditingTitle(false);
                  setTitleInput(receipt.title);
                }}
              >
                <Text style={[styles.actionButtonText, styles.deleteButtonText]}>Cancel</Text>
              </Pressable>
            </>
          ) : (
            <>
              <Pressable
                style={styles.actionButton}
                onPress={() => {
                  setTitleInput(receipt.title);
                  setEditingTitle(true);
                }}
              >
                <Text style={styles.actionButtonText}>Edit Receipt</Text>
              </Pressable>
              <Pressable
                style={[styles.actionButton, styles.deleteButton]}
                onPress={() => {
                  Alert.alert('Delete receipt?', 'Deleting a receipt will also remove all its items.', [
                    { text: 'Cancel', style: 'cancel' },
                    {
                      text: 'Delete',
                      style: 'destructive',
                      onPress: async () => {
                        await deleteReceipt.mutateAsync(receiptId);
                        navigation.navigate('ReceiptsHome');
                      },
                    },
                  ]);
                }}
              >
                <Text style={[styles.actionButtonText, styles.deleteButtonText]}>Delete Receipt</Text>
              </Pressable>
            </>
          )}
        </View>
      </View>
      </View>

      <View style={styles.tabs}>
        {(['SCAN', 'ITEMS', 'JSON'] as const).map((key) => (
          <Pressable
            key={key}
            style={[styles.tabButton, tab === key && styles.tabButtonActive]}
            onPress={() => setTab(key)}
          >
            <Text style={styles.tabButtonText}>{key}</Text>
          </Pressable>
        ))}
      </View>

      {tab === 'SCAN' ? (
        <View style={styles.panel}>
          <Text style={styles.meta}>Image URI: {receipt.image_uri ?? 'No image attached'}</Text>
          <View style={styles.row}>
            <Pressable style={styles.actionButton} onPress={() => navigation.navigate('Scan', { receiptId })}>
              <Text style={styles.actionButtonText}>Re-scan</Text>
            </Pressable>
            <Pressable
              style={styles.actionButton}
              onPress={async () => {
                await updateReceipt.mutateAsync({ receiptId, status: 'PROCESSING' });
              }}
            >
              <Text style={styles.actionButtonText}>Retry Parse</Text>
            </Pressable>
            <Pressable
              style={[styles.actionButton, styles.deleteButton]}
              onPress={async () => {
                await updateReceipt.mutateAsync({ receiptId, status: 'FAILED' });
              }}
            >
              <Text style={[styles.actionButtonText, styles.deleteButtonText]}>Cancel</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      {tab === 'ITEMS' ? (
        <ScrollView style={styles.panel} contentContainerStyle={styles.panelContent}>
          <View style={styles.addItemCard}>
            <Text style={styles.sectionTitle}>Add Item</Text>
            <TextInput style={styles.input} value={newName} onChangeText={setNewName} placeholder="Item name" />
            <View style={styles.row}>
              <TextInput
                style={[styles.input, styles.smallInput]}
                value={newQty}
                onChangeText={setNewQty}
                keyboardType="numeric"
                placeholder="Qty"
              />
              <TextInput
                style={[styles.input, styles.smallInput]}
                value={newUnitPrice}
                onChangeText={setNewUnitPrice}
                keyboardType="numeric"
                placeholder="Unit price"
              />
            </View>
            <Pressable
              style={styles.actionButton}
              onPress={async () => {
                const safeName = newName.trim();
                if (!safeName) return;
                const qty = Math.max(0.01, toNumber(newQty, 1));
                const unitPrice = Math.max(0, toNumber(newUnitPrice, 0));
                await addItem.mutateAsync({ receiptId, name: safeName, qty, unitPrice });
                setNewName('');
                setNewQty('1');
                setNewUnitPrice('0');
              }}
            >
              <Text style={styles.actionButtonText}>Add</Text>
            </Pressable>
          </View>

          {activeItems.map((item) => (
            <EditableItemRow key={item.id} item={item} receiptId={receiptId} />
          ))}

          {receipt.status === 'NEEDS_REVIEW' ? (
            <Pressable
              style={[styles.actionButton, styles.confirmButton]}
              onPress={async () => {
                await confirmReceipt.mutateAsync(receiptId);
              }}
            >
              <Text style={styles.confirmButtonText}>Confirm Receipt</Text>
            </Pressable>
          ) : null}
        </ScrollView>
      ) : null}

      {tab === 'JSON' ? (
        <ScrollView style={styles.panel} contentContainerStyle={styles.panelContent}>
          <Text style={styles.jsonText}>{JSON.stringify(receipt, null, 2)}</Text>
        </ScrollView>
      ) : null}
    </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f8fafc' },
  container: { flex: 1, backgroundColor: '#f8fafc', paddingHorizontal: 12, paddingBottom: 12 },
  loaderWrap: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { color: '#64748b' },
  navRow: { paddingTop: 4, marginBottom: 8 },
  backButton: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 999,
    backgroundColor: '#fff',
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  backText: { color: '#0f172a', fontSize: 12, fontWeight: '700' },
  headerCard: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    backgroundColor: '#fff',
    padding: 10,
    marginBottom: 10,
  },
  header: { marginBottom: 10 },
  title: { fontSize: 20, fontWeight: '700', color: '#0f172a' },
  titleInput: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    backgroundColor: '#fff',
    paddingHorizontal: 10,
    paddingVertical: 8,
    color: '#0f172a',
    fontSize: 18,
    fontWeight: '700',
  },
  meta: { marginTop: 3, color: '#475569', fontSize: 12 },
  tabs: { flexDirection: 'row', gap: 8, marginBottom: 10 },
  tabButton: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    backgroundColor: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  tabButtonActive: { borderColor: '#2563eb', backgroundColor: '#dbeafe' },
  tabButtonText: { fontWeight: '600', color: '#0f172a', fontSize: 12 },
  panel: { flex: 1 },
  panelContent: { paddingBottom: 24, gap: 8 },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 8 },
  addItemCard: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    backgroundColor: '#fff',
    padding: 10,
  },
  itemCard: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    backgroundColor: '#fff',
    padding: 10,
  },
  row: { flexDirection: 'row', gap: 8, marginTop: 8 },
  input: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    backgroundColor: '#fff',
    paddingHorizontal: 10,
    paddingVertical: 8,
    color: '#111827',
    flex: 1,
  },
  smallInput: { flex: 1 },
  actionButton: {
    backgroundColor: '#2563eb',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 9,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionButtonText: { color: '#fff', fontWeight: '700', fontSize: 12 },
  deleteButton: { backgroundColor: '#fee2e2' },
  deleteButtonText: { color: '#b91c1c' },
  confirmButton: { marginTop: 6, backgroundColor: '#16a34a' },
  confirmButtonText: { color: '#fff', fontWeight: '700' },
  jsonText: {
    fontFamily: 'Courier',
    fontSize: 12,
    color: '#0f172a',
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    padding: 10,
  },
});
