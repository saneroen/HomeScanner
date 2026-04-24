import React, { useMemo } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { lifeEventTemplates, regionVendors } from '../../features/savings/mockData';
import { TopNavShell } from '../../ui/components/TopNavShell';
import { useAppUi } from '../../ui/state/AppUiContext';

export function LifeEventsScreen() {
  const { region, cycleRegion, searches, setSearch } = useAppUi();

  const rows = useMemo(() => {
    const query = searches.lifeEvents.trim().toLowerCase();
    const allowedVendors = new Set(regionVendors(region));

    return lifeEventTemplates.filter((template) => {
      const regionPass = template.trackedVendors.some((vendor) => allowedVendors.has(vendor));
      const queryPass =
        !query ||
        template.title.toLowerCase().includes(query) ||
        template.description.toLowerCase().includes(query) ||
        template.trackedCategories.some((entry) => entry.toLowerCase().includes(query));
      return regionPass && queryPass;
    });
  }, [region, searches.lifeEvents]);

  return (
    <TopNavShell
      regionLabel={region}
      onRegionPress={cycleRegion}
      searchPlaceholder="Search life events"
      searchValue={searches.lifeEvents}
      onSearchChange={(value) => setSearch('lifeEvents', value)}
    >
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>

      {rows.map((template) => (
        <View key={template.id} style={styles.card}>
          <Text style={styles.cardTitle}>{template.title}</Text>
          <Text style={styles.cardDescription}>{template.description}</Text>

          <Text style={styles.metaTitle}>Tracked Categories</Text>
          <Text style={styles.metaText}>{template.trackedCategories.join(' · ')}</Text>

          <Text style={styles.metaTitle}>Preferred Vendors</Text>
          <Text style={styles.metaText}>{template.trackedVendors.join(' · ')}</Text>

          <Pressable style={styles.activateButton}>
            <Text style={styles.activateButtonText}>Activate Event Plan</Text>
          </Pressable>
        </View>
      ))}

      <Pressable style={styles.customButton}>
        <Text style={styles.customButtonText}>+ Add Custom Life Event</Text>
      </Pressable>

      {rows.length === 0 ? <Text style={styles.empty}>No events match this region or search.</Text> : null}
      </ScrollView>
    </TopNavShell>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingTop: 12, paddingBottom: 28, gap: 12 },
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    padding: 14,
  },
  cardTitle: { color: '#0f172a', fontWeight: '700', fontSize: 17 },
  cardDescription: { marginTop: 4, color: '#475569', fontSize: 13 },
  metaTitle: { marginTop: 10, color: '#334155', fontWeight: '700', fontSize: 12 },
  metaText: { marginTop: 3, color: '#64748b', fontSize: 12 },
  activateButton: {
    marginTop: 12,
    backgroundColor: '#2563eb',
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: 'center',
  },
  activateButtonText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  customButton: {
    marginTop: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#fff',
    paddingVertical: 12,
    alignItems: 'center',
  },
  customButtonText: { color: '#0f172a', fontWeight: '700' },
  empty: { marginTop: 8, textAlign: 'center', color: '#64748b' },
});
