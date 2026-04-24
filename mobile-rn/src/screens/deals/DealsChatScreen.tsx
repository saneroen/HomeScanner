import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { inventoryMonthlyRows, regionVendors } from '../../features/savings/mockData';
import { TopNavShell } from '../../ui/components/TopNavShell';
import { useAppUi } from '../../ui/state/AppUiContext';

const FILTERS = ['All Vendors', 'Nearby', 'Online', 'Grocery', 'Furniture', 'Clothing'] as const;

export function DealsChatScreen() {
  const [selectedFilter, setSelectedFilter] = useState<(typeof FILTERS)[number]>('All Vendors');
  const [activeFeed, setActiveFeed] = useState<'frequent' | 'custom'>('frequent');
  const [editingAlertId, setEditingAlertId] = useState<string | null>(null);
  const [editingTargetPrice, setEditingTargetPrice] = useState('');
  const { region, cycleRegion, searches, setSearch, watchlistAlerts, addWatchlistAlert, updateWatchlistAlert } = useAppUi();

  const query = searches.deals.trim().toLowerCase();
  const allowedVendors = new Set(regionVendors(region));

  const matchesSelectedFilter = useMemo(
    () =>
      (vendor: string, category: string) => {
        if (selectedFilter === 'All Vendors') return true;
        if (selectedFilter === 'Nearby') return allowedVendors.has(vendor);
        if (selectedFilter === 'Online') return vendor.toLowerCase().includes('amazon') || vendor.toLowerCase().includes('online');
        return category.toLowerCase() === selectedFilter.toLowerCase();
      },
    [allowedVendors, selectedFilter],
  );

  const watchRows = watchlistAlerts.filter((item) => {
    const regionPass = allowedVendors.has(item.vendor);
    const queryPass = !query || item.item.toLowerCase().includes(query) || item.vendor.toLowerCase().includes(query);
    const filterPass = matchesSelectedFilter(item.vendor, item.category);
    return regionPass && queryPass && filterPass;
  });

  const frequentPurchases = inventoryMonthlyRows.filter((item) => {
    const regionPass = allowedVendors.has(item.vendor);
    const queryPass =
      !query ||
      item.itemName.toLowerCase().includes(query) ||
      item.vendor.toLowerCase().includes(query) ||
      item.category.toLowerCase().includes(query);
    const filterPass = matchesSelectedFilter(item.vendor, item.category);
    return regionPass && queryPass && filterPass;
  });

  const topRecommendation = frequentPurchases[0] ?? null;

  const addRecommendedAlert = () => {
    if (!topRecommendation) return;
    addWatchlistAlert({
      item: topRecommendation.itemName,
      vendor: topRecommendation.vendor,
      category: topRecommendation.category as 'Grocery' | 'Furniture' | 'Clothing' | 'Electronics' | 'Other',
      targetPrice: Number((topRecommendation.currentBestPrice * 0.92).toFixed(2)),
    });
    setActiveFeed('custom');
  };

  const startEdit = (id: string, currentTarget: number) => {
    setEditingAlertId(id);
    setEditingTargetPrice(currentTarget.toFixed(2));
  };

  const saveEdit = (id: string) => {
    const parsed = Number(editingTargetPrice);
    updateWatchlistAlert(id, {
      targetPrice: Number.isFinite(parsed) ? parsed : 0,
    });
    setEditingAlertId(null);
    setEditingTargetPrice('');
  };

  return (
    <TopNavShell
      regionLabel={region}
      onRegionPress={cycleRegion}
      searchPlaceholder="Search deals and watchlist"
      searchValue={searches.deals}
      onSearchChange={(value) => setSearch('deals', value)}
    >
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        nestedScrollEnabled
        directionalLockEnabled
        keyboardShouldPersistTaps="handled"
      >
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Deal Assistant</Text>
        {query && topRecommendation ? (
          <Text style={styles.assistText}>
            Based on “{searches.deals}”, we recommend {topRecommendation.itemName} at {topRecommendation.vendor} for ${topRecommendation.currentBestPrice.toFixed(2)}.
          </Text>
        ) : (
          <Text style={styles.assistText}>Search for an item and we’ll recommend the best deal. You can set a price alert at the end.</Text>
        )}
        <Pressable
          style={[styles.addButton, !topRecommendation && styles.addButtonDisabled]}
          onPress={addRecommendedAlert}
          disabled={!topRecommendation}
        >
          <Text style={styles.addButtonText}>Set Price Alert</Text>
        </Pressable>
      </View>

      <View style={styles.toggleWrap}>
        <Pressable
          style={[styles.toggleButton, activeFeed === 'frequent' && styles.toggleButtonActive]}
          onPress={() => setActiveFeed('frequent')}
        >
          <Text style={[styles.toggleText, activeFeed === 'frequent' && styles.toggleTextActive]}>Frequent Purchase Deals</Text>
        </Pressable>
        <Pressable
          style={[styles.toggleButton, activeFeed === 'custom' && styles.toggleButtonActive]}
          onPress={() => setActiveFeed('custom')}
        >
          <Text style={[styles.toggleText, activeFeed === 'custom' && styles.toggleTextActive]}>Custom Alerts</Text>
        </Pressable>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.filtersScroll}
        nestedScrollEnabled
        directionalLockEnabled
      >
        <View style={styles.filtersRow}>
          {FILTERS.map((filter) => (
            <Pressable
              key={filter}
              style={[styles.filterChip, selectedFilter === filter && styles.filterChipActive]}
              onPress={() => setSelectedFilter(filter)}
            >
              <Text style={styles.filterChipText}>{filter}</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>

      {activeFeed === 'frequent' ? (
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Frequent Purchases (Inventory)</Text>
        {frequentPurchases.map((item) => (
          <View key={item.id} style={styles.watchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.watchItem}>{item.itemName}</Text>
              <Text style={styles.watchMeta}>{item.category} · {item.vendor}</Text>
            </View>
            <View>
              <Text style={styles.watchPrice}>Now ${item.currentBestPrice.toFixed(2)}</Text>
              <Text style={styles.watchMeta}>Paid ${item.paidPrice.toFixed(2)}</Text>
            </View>
          </View>
        ))}

        {frequentPurchases.length === 0 ? <Text style={styles.empty}>No inventory-based deal rows for this filter.</Text> : null}
      </View>
      ) : (
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Custom Watchlist Alerts</Text>
        {watchRows.map((item) => (
          <View key={item.id} style={styles.watchRowStack}>
            <View style={styles.watchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.watchItem}>{item.item}</Text>
                <Text style={styles.watchMeta}>Vendor: {item.vendor}</Text>
              </View>
              <View>
                <Text style={styles.watchPrice}>Best ${item.currentBest.toFixed(2)}</Text>
                <Text style={styles.watchMeta}>Target ${item.targetPrice.toFixed(2)}</Text>
              </View>
            </View>

            {editingAlertId === item.id ? (
              <View style={styles.editRow}>
                <TextInput
                  value={editingTargetPrice}
                  onChangeText={setEditingTargetPrice}
                  keyboardType="decimal-pad"
                  style={styles.editInput}
                  placeholder="Target $"
                  placeholderTextColor="#94a3b8"
                />
                <Pressable style={styles.inlineAction} onPress={() => saveEdit(item.id)}>
                  <Text style={styles.inlineActionText}>Save</Text>
                </Pressable>
                <Pressable
                  style={[styles.inlineAction, styles.inlineActionGhost]}
                  onPress={() => {
                    setEditingAlertId(null);
                    setEditingTargetPrice('');
                  }}
                >
                  <Text style={[styles.inlineActionText, styles.inlineActionGhostText]}>Cancel</Text>
                </Pressable>
              </View>
            ) : (
              <View style={styles.editRow}>
                <Pressable style={[styles.inlineAction, styles.inlineActionGhost]} onPress={() => startEdit(item.id, item.targetPrice)}>
                  <Text style={[styles.inlineActionText, styles.inlineActionGhostText]}>Edit</Text>
                </Pressable>
              </View>
            )}

            <View style={styles.rowDivider} />
          </View>
        ))}

        {watchRows.length === 0 ? <Text style={styles.empty}>No watchlist matches this region or search.</Text> : null}
      </View>
      )}
      </ScrollView>
    </TopNavShell>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { gap: 12, paddingTop: 12, paddingBottom: 30 },
  filtersScroll: { maxHeight: 46 },
  filtersRow: { flexDirection: 'row', gap: 8, paddingRight: 8 },
  filterChip: {
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#fff',
  },
  filterChipActive: { backgroundColor: '#dbeafe', borderColor: '#60a5fa' },
  filterChipText: { color: '#0f172a', fontSize: 12, fontWeight: '600' },
  card: { borderRadius: 14, backgroundColor: '#fff', borderWidth: 1, borderColor: '#e2e8f0', padding: 14 },
  sectionTitle: { color: '#0f172a', fontWeight: '700', fontSize: 16, marginBottom: 6 },
  assistText: { color: '#334155', fontSize: 12, lineHeight: 18 },
  addButton: {
    marginTop: 10,
    borderRadius: 10,
    backgroundColor: '#1d4ed8',
    paddingVertical: 9,
    alignItems: 'center',
  },
  addButtonDisabled: { backgroundColor: '#93c5fd' },
  addButtonText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  toggleWrap: {
    flexDirection: 'row',
    backgroundColor: '#e2e8f0',
    borderRadius: 999,
    padding: 4,
    gap: 4,
  },
  toggleButton: {
    flex: 1,
    alignItems: 'center',
    borderRadius: 999,
    paddingVertical: 8,
  },
  toggleButtonActive: { backgroundColor: '#ffffff' },
  toggleText: { color: '#64748b', fontSize: 12, fontWeight: '700' },
  toggleTextActive: { color: '#0f172a' },
  watchRowStack: { marginTop: 10 },
  watchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  watchItem: { color: '#0f172a', fontWeight: '700', fontSize: 14 },
  watchMeta: { color: '#64748b', fontSize: 12 },
  watchPrice: { color: '#0f172a', fontWeight: '700', textAlign: 'right' },
  editRow: { marginTop: 8, flexDirection: 'row', gap: 8, alignItems: 'center' },
  editInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 7,
    color: '#0f172a',
    fontSize: 12,
  },
  inlineAction: {
    borderRadius: 8,
    backgroundColor: '#1d4ed8',
    paddingHorizontal: 10,
    paddingVertical: 7,
  },
  inlineActionGhost: { backgroundColor: '#e2e8f0' },
  inlineActionText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  inlineActionGhostText: { color: '#334155' },
  rowDivider: { marginTop: 10, height: 1, backgroundColor: '#eef2f7' },
  empty: { marginTop: 10, color: '#64748b' },
});
