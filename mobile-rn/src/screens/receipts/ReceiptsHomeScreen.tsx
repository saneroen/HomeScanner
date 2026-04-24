import { NativeStackScreenProps } from '@react-navigation/native-stack';
import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { useHealthQuery } from '../../api/health';
import { ReceiptStatus, useCreateReceiptMutation, useReceiptsQuery } from '../../api/receipts';
import { ENV } from '../../config/env';
import { ReceiptsStackParamList } from '../../navigation/types';
import { TopNavShell } from '../../ui/components/TopNavShell';
import { useAppUi } from '../../ui/state/AppUiContext';

type Props = NativeStackScreenProps<ReceiptsStackParamList, 'ReceiptsHome'>;

const STATUS_FILTERS: Array<'ALL' | ReceiptStatus> = [
  'ALL',
  'CAPTURED_RAW',
  'PROCESSING',
  'NEEDS_REVIEW',
  'CONFIRMED',
  'FAILED',
];

export function ReceiptsHomeScreen({ navigation }: Props) {
  const [selectedStatus, setSelectedStatus] = useState<'ALL' | ReceiptStatus>('ALL');
  const { region, cycleRegion, searches, setSearch } = useAppUi();

  const healthQuery = useHealthQuery();
  const receiptsQuery = useReceiptsQuery();
  const createReceipt = useCreateReceiptMutation();

  const filtered = useMemo(() => {
    const query = searches.receipts.trim().toLowerCase();
    return (receiptsQuery.data ?? []).filter((receipt) => {
      const statusPass = selectedStatus === 'ALL' || receipt.status === selectedStatus;
      const searchPass =
        !query ||
        receipt.title.toLowerCase().includes(query) ||
        receipt.id.toLowerCase().includes(query);
      return statusPass && searchPass;
    });
  }, [receiptsQuery.data, searches.receipts, selectedStatus]);

  const healthText = healthQuery.isError
    ? `Backend unreachable at ${ENV.API_BASE_URL}`
    : `Connected: ${healthQuery.data?.status ?? 'unknown'} (${healthQuery.data?.env ?? 'unknown'})`;

  return (
    <TopNavShell
      regionLabel={region}
      onRegionPress={cycleRegion}
      searchPlaceholder="Search receipts"
      searchValue={searches.receipts}
      onSearchChange={(value) => setSearch('receipts', value)}
    >
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Pressable style={styles.scanButton} onPress={() => navigation.navigate('Scan')}>
          <Text style={styles.scanButtonText}>Scan</Text>
        </Pressable>
        <Pressable
          style={styles.newDraftButton}
          onPress={async () => {
            const created = await createReceipt.mutateAsync(undefined);
            navigation.navigate('ReceiptDetails', { receiptId: created.id });
          }}
        >
          <Text style={styles.newDraftButtonText}>Create Draft</Text>
        </Pressable>
      </View>

      {healthQuery.isLoading ? (
        <ActivityIndicator size="small" style={styles.healthLoader} />
      ) : (
        <Text style={[styles.healthText, healthQuery.isError ? styles.healthError : styles.healthOk]}>
          {healthText}
        </Text>
      )}

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filtersScroll}>
        <View style={styles.filtersRow}>
          {STATUS_FILTERS.map((statusValue) => (
            <Pressable
              key={statusValue}
              style={[styles.chip, selectedStatus === statusValue && styles.chipActive]}
              onPress={() => setSelectedStatus(statusValue)}
            >
              <Text style={styles.chipText}>{statusValue}</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>

      <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
        {receiptsQuery.isLoading ? <ActivityIndicator size="small" /> : null}

        {filtered.map((receipt) => (
          <Pressable
            key={receipt.id}
            style={styles.card}
            onPress={() => navigation.navigate('ReceiptDetails', { receiptId: receipt.id })}
          >
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>{receipt.title}</Text>
              <Text style={styles.cardStatus}>{receipt.status}</Text>
            </View>
            <Text style={styles.cardMeta}>Items: {receipt.items?.filter((item) => item.deleted === 0).length ?? 0}</Text>
            <Text style={styles.cardMeta}>Total: {receipt.total != null ? `$${receipt.total.toFixed(2)}` : 'N/A'}</Text>
            <Text style={styles.cardMeta}>Updated: {new Date(receipt.updated_at).toLocaleString()}</Text>
          </Pressable>
        ))}

        {!receiptsQuery.isLoading && filtered.length === 0 ? (
          <Text style={styles.emptyState}>No receipts found for current filters.</Text>
        ) : null}
      </ScrollView>
    </View>
    </TopNavShell>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc', paddingTop: 12 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  scanButton: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
  },
  scanButtonText: { color: '#fff', fontWeight: '700' },
  healthLoader: { marginTop: 10, alignSelf: 'flex-start' },
  healthText: { marginTop: 8, fontSize: 12 },
  healthOk: { color: '#15803d' },
  healthError: { color: '#b91c1c' },
  newDraftButton: {
    backgroundColor: '#0f766e',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 10,
    alignItems: 'center',
  },
  newDraftButtonText: { color: '#fff', fontWeight: '700' },
  filtersScroll: { marginTop: 10, maxHeight: 44 },
  filtersRow: { flexDirection: 'row', gap: 8, paddingRight: 8 },
  chip: {
    borderColor: '#cbd5e1',
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: '#fff',
  },
  chipActive: { borderColor: '#2563eb', backgroundColor: '#dbeafe' },
  chipText: { fontSize: 12, fontWeight: '600', color: '#0f172a' },
  list: { marginTop: 10, flex: 1 },
  listContent: { paddingBottom: 24, gap: 8 },
  card: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    backgroundColor: '#fff',
    padding: 12,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  cardTitle: { fontSize: 16, fontWeight: '700', color: '#111827', flex: 1 },
  cardStatus: { fontSize: 11, fontWeight: '700', color: '#334155' },
  cardMeta: { marginTop: 4, color: '#64748b', fontSize: 12 },
  emptyState: { marginTop: 24, textAlign: 'center', color: '#64748b' },
});
