import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { frequentItemSavings, vendorAvailableInRegion } from '../../features/savings/mockData';
import { TopNavShell } from '../../ui/components/TopNavShell';
import { useAppUi } from '../../ui/state/AppUiContext';

const VENDORS = ['ALL', 'Aldi', 'Costco', 'Amazon', 'Old Navy', 'Walmart'] as const;

export function SavingsOpportunitiesScreen() {
  const [vendor, setVendor] = useState<(typeof VENDORS)[number]>('ALL');
  const { region, cycleRegion, searches, setSearch } = useAppUi();

  const rows = useMemo(() => {
    const query = searches.savings.trim().toLowerCase();
    return frequentItemSavings.filter((item) => {
      const vendorPass = vendor === 'ALL' || item.bestVendor === vendor;
      const regionPass = vendorAvailableInRegion(region, item.bestVendor);
      const queryPass =
        !query ||
        item.itemName.toLowerCase().includes(query) ||
        item.category.toLowerCase().includes(query) ||
        item.bestVendor.toLowerCase().includes(query);
      return vendorPass && regionPass && queryPass;
    });
  }, [region, searches.savings, vendor]);

  const totalPossible = rows.reduce((sum, item) => sum + item.savingsPerMonth, 0);

  return (
    <TopNavShell
      regionLabel={region}
      onRegionPress={cycleRegion}
      searchPlaceholder="Search savings opportunities"
      searchValue={searches.savings}
      onSearchChange={(value) => setSearch('savings', value)}
    >
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      <View style={styles.totalCard}>
        <Text style={styles.totalLabel}>Potential monthly savings</Text>
        <Text style={styles.totalValue}>${totalPossible.toFixed(2)}</Text>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filtersScroll}>
        <View style={styles.filtersRow}>
          {VENDORS.map((entry) => (
            <Pressable
              key={entry}
              style={[styles.filterChip, vendor === entry && styles.filterChipActive]}
              onPress={() => setVendor(entry)}
            >
              <Text style={styles.filterChipText}>{entry}</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>

      {rows.map((item) => (
        <View key={item.id} style={styles.rowCard}>
          <View style={styles.rowTop}>
            <Text style={styles.itemName}>{item.itemName}</Text>
            <Text style={styles.itemSaving}>${item.savingsPerMonth.toFixed(2)}/mo</Text>
          </View>
          <Text style={styles.rowMeta}>{item.category} · {item.frequencyPerMonth}x/month</Text>
          <Text style={styles.rowMeta}>
            Avg ${item.avgPaidPrice.toFixed(2)} → Best ${item.bestDealPrice.toFixed(2)} at {item.bestVendor}
          </Text>
        </View>
      ))}

      {rows.length === 0 ? <Text style={styles.empty}>No opportunities for this region/filter.</Text> : null}
      </ScrollView>
    </TopNavShell>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingTop: 12, paddingBottom: 32, gap: 12 },
  totalCard: { borderRadius: 16, backgroundColor: '#0c4a6e', padding: 16 },
  totalLabel: { color: '#bae6fd', fontSize: 13 },
  totalValue: { color: '#fff', fontSize: 34, fontWeight: '800' },
  filtersScroll: { maxHeight: 46 },
  filtersRow: { flexDirection: 'row', gap: 8, paddingRight: 8 },
  filterChip: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  filterChipActive: { borderColor: '#2563eb', backgroundColor: '#dbeafe' },
  filterChipText: { color: '#0f172a', fontSize: 12, fontWeight: '600' },
  rowCard: { borderRadius: 14, borderWidth: 1, borderColor: '#e2e8f0', backgroundColor: '#fff', padding: 14 },
  rowTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  itemName: { color: '#0f172a', fontWeight: '700', fontSize: 15, flex: 1 },
  itemSaving: { color: '#047857', fontWeight: '800', marginLeft: 8 },
  rowMeta: { marginTop: 4, color: '#64748b', fontSize: 12 },
  empty: { marginTop: 8, textAlign: 'center', color: '#64748b' },
});
