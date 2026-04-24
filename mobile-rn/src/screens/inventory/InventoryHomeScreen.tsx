import { NativeStackScreenProps } from '@react-navigation/native-stack';
import React, { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { useInventoryItemsQuery } from '../../api/inventory';
import { InventoryStackParamList } from '../../navigation/types';
import { TopNavShell } from '../../ui/components/TopNavShell';
import { useAppUi } from '../../ui/state/AppUiContext';

type Props = NativeStackScreenProps<InventoryStackParamList, 'InventoryHome'>;

export function InventoryHomeScreen({ navigation }: Props) {
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const { region, cycleRegion, searches, setSearch } = useAppUi();
  const inventoryQuery = useInventoryItemsQuery();

  const categories = useMemo(
    () => ['ALL', ...Array.from(new Set((inventoryQuery.data ?? []).map((item) => item.category).filter(Boolean)))],
    [inventoryQuery.data],
  );

  const filteredItems = useMemo(() => {
    const query = searches.inventory.trim().toLowerCase();
    return (inventoryQuery.data ?? []).filter((item) => {
      const categoryPass = selectedCategory === 'ALL' || item.category === selectedCategory;
      const searchPass =
        !query ||
        item.display_name.toLowerCase().includes(query) ||
        item.category.toLowerCase().includes(query) ||
        item.normalized_key.toLowerCase().includes(query);
      return categoryPass && searchPass;
    });
  }, [inventoryQuery.data, searches.inventory, selectedCategory]);

  const totalPaid = filteredItems.reduce((sum, row) => sum + (row.last_purchase_price ?? 0), 0);

  return (
    <TopNavShell
      regionLabel={region}
      onRegionPress={cycleRegion}
      searchPlaceholder="Search inventory items"
      searchValue={searches.inventory}
      onSearchChange={(value) => setSearch('inventory', value)}
    >
      <View style={styles.container}>

      <View style={styles.summaryCard}>
        <Text style={styles.summaryLabel}>Inventory spend this month</Text>
        <Text style={styles.summaryValue}>${totalPaid.toFixed(2)}</Text>
        <Text style={styles.summaryMeta}>{filteredItems.length} inventory items from API</Text>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll}>
        <View style={styles.chipsRow}>
          {categories.map((category) => (
            <Pressable
              key={category}
              style={[styles.chip, selectedCategory === category && styles.chipActive]}
              onPress={() => setSelectedCategory(category)}
            >
              <Text style={styles.chipText}>{category}</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>

      <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
        {inventoryQuery.isLoading ? <ActivityIndicator size="small" /> : null}
        {inventoryQuery.isError ? <Text style={styles.empty}>Unable to load inventory items from API.</Text> : null}

        {filteredItems.map((item) => (
          <Pressable
            key={item.id}
            style={styles.card}
            onPress={() => navigation.navigate('InventoryItemDetails', { itemId: item.id })}
          >
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>{item.display_name}</Text>
              <Text style={styles.cardSaving}>{item.purchase_count}x</Text>
            </View>
            <Text style={styles.cardMeta}>Category: {item.category}</Text>
            <Text style={styles.cardMeta}>Last purchase: {item.last_purchased_at ? new Date(item.last_purchased_at).toLocaleDateString() : 'N/A'}</Text>
            <Text style={styles.cardMeta}>Last price: {item.last_purchase_price != null ? `$${item.last_purchase_price.toFixed(2)}` : 'N/A'}</Text>
          </Pressable>
        ))}

        {filteredItems.length === 0 ? (
          <Text style={styles.empty}>No inventory items found.</Text>
        ) : null}
      </ScrollView>
      </View>
    </TopNavShell>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc', paddingTop: 12 },
  summaryCard: {
    borderRadius: 14,
    backgroundColor: '#0f172a',
    padding: 14,
  },
  summaryLabel: { color: '#cbd5e1', fontSize: 12 },
  summaryValue: { color: '#fff', fontSize: 30, fontWeight: '800', marginTop: 2 },
  summaryMeta: { marginTop: 4, color: '#93c5fd', fontSize: 12 },
  chipsScroll: { marginTop: 10, maxHeight: 44 },
  chipsRow: { flexDirection: 'row', gap: 8, paddingRight: 8 },
  chip: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 999,
    backgroundColor: '#fff',
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  chipActive: { backgroundColor: '#dcfce7', borderColor: '#22c55e' },
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
  cardTitle: { fontSize: 16, fontWeight: '700', color: '#111827' },
  cardSaving: { color: '#0f172a', fontSize: 12, fontWeight: '700' },
  cardMeta: { marginTop: 4, color: '#64748b', fontSize: 12 },
  empty: { marginTop: 24, textAlign: 'center', color: '#64748b' },
});
