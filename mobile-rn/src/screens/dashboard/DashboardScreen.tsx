import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import {
  monthlySavingsSummary,
  userPriceAlerts,
  vendorAvailableInRegion,
  vendorShiftSuggestions,
} from '../../features/savings/mockData';
import { TopNavShell } from '../../ui/components/TopNavShell';
import { useAppUi } from '../../ui/state/AppUiContext';

export function DashboardScreen() {
  const [editingAlertId, setEditingAlertId] = useState<string | null>(null);
  const [editingTargetPrice, setEditingTargetPrice] = useState('');
  const { region, cycleRegion, searches, setSearch, watchlistAlerts, addWatchlistAlert, updateWatchlistAlert } = useAppUi();

  const shifts = useMemo(() => {
    const query = searches.dashboard.trim().toLowerCase();
    return vendorShiftSuggestions.filter((shift) => {
      const regionPass = vendorAvailableInRegion(region, shift.toVendor);
      if (!regionPass) {
        return false;
      }
      if (!query) {
        return true;
      }
      return (
        shift.itemName.toLowerCase().includes(query) ||
        shift.fromVendor.toLowerCase().includes(query) ||
        shift.toVendor.toLowerCase().includes(query) ||
        shift.bestDay.toLowerCase().includes(query)
      );
    });
  }, [region, searches.dashboard]);

  const visibleMonthlyImpact = shifts.reduce((sum, shift) => sum + shift.monthlyImpact, 0);

  const priceAlerts = useMemo(() => {
    const query = searches.dashboard.trim().toLowerCase();
    const fromDefault = userPriceAlerts.filter((alert) => {
      if (!query) {
        return true;
      }
      return (
        alert.itemName.toLowerCase().includes(query) ||
        alert.preferredVendor.toLowerCase().includes(query) ||
        alert.triggerType.toLowerCase().includes(query)
      );
    });
    const fromWatchlist = watchlistAlerts
      .filter((alert) => {
        if (!query) return true;
        return alert.item.toLowerCase().includes(query) || alert.vendor.toLowerCase().includes(query) || alert.category.toLowerCase().includes(query);
      })
      .map((alert) => ({
        id: `wl-${alert.id}`,
        itemName: alert.item,
        targetPrice: alert.targetPrice,
        currentBestPrice: alert.currentBest,
        preferredVendor: alert.vendor,
        triggerType: 'DEAL_ALERT' as const,
      }));

    return [...fromWatchlist, ...fromDefault];
  }, [searches.dashboard, watchlistAlerts]);

  const topRecommendation = shifts[0] ?? null;

  const setAlertFromRecommendation = () => {
    if (!topRecommendation) return;
    addWatchlistAlert({
      item: topRecommendation.itemName,
      targetPrice: Number((topRecommendation.betterPrice * 0.95).toFixed(2)),
      vendor: topRecommendation.toVendor,
      category: 'Other',
    });
  };

  const startEdit = (id: string, currentTarget: number) => {
    setEditingAlertId(id);
    setEditingTargetPrice(currentTarget.toFixed(2));
  };

  const saveEdit = (id: string) => {
    const parsed = Number(editingTargetPrice);
    updateWatchlistAlert(id.replace('wl-', ''), {
      targetPrice: Number.isFinite(parsed) ? parsed : 0,
    });
    setEditingAlertId(null);
    setEditingTargetPrice('');
  };

  return (
    <TopNavShell
      regionLabel={region}
      onRegionPress={cycleRegion}
      searchPlaceholder="Search savings, deals, or alerts"
      searchValue={searches.dashboard}
      onSearchChange={(value) => setSearch('dashboard', value)}
    >
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        nestedScrollEnabled
        directionalLockEnabled
      >
      <View style={styles.heroCard}>
        <Text style={styles.heroLabel}>{monthlySavingsSummary.monthLabel}</Text>
        <Text style={styles.heroValue}>${monthlySavingsSummary.totalSavings.toFixed(2)}</Text>
        <Text style={styles.heroMeta}>
          Potential additional savings: ${monthlySavingsSummary.potentialSavings.toFixed(2)}
        </Text>
      </View>

        <View style={styles.kpiRow}>
        <View style={styles.kpiCard}>
          <Text style={styles.kpiTitle}>Vendor Shifts</Text>
          <Text style={styles.kpiValue}>{shifts.length}</Text>
        </View>
        <View style={styles.kpiCard}>
          <Text style={styles.kpiTitle}>Visible Savings</Text>
          <Text style={styles.kpiValue}>${visibleMonthlyImpact.toFixed(0)}</Text>
        </View>
        </View>

        <Text style={styles.sectionTitle}>Deals</Text>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.carouselRow}
          nestedScrollEnabled
          directionalLockEnabled
        >
          {shifts.map((shift) => (
            <View key={shift.id} style={styles.carouselCard}>
              <Text style={styles.cardTitle}>{shift.itemName}</Text>
              <Text style={styles.cardMeta}>{shift.fromVendor} → {shift.toVendor}</Text>
              <Text style={styles.cardMeta}>Best day: {shift.bestDay}</Text>
              <Text style={styles.cardMeta}>${shift.currentPrice.toFixed(2)} → ${shift.betterPrice.toFixed(2)} · Save ${shift.monthlyImpact.toFixed(2)}/mo</Text>
            </View>
          ))}
        </ScrollView>

        {shifts.length === 0 ? <Text style={styles.empty}>No dashboard matches for this region or search.</Text> : null}

        <View style={styles.formCard}>
          <Text style={styles.sectionTitle}>Deal Assistant</Text>
          {searches.dashboard.trim() && topRecommendation ? (
            <Text style={styles.assistText}>
              Based on “{searches.dashboard}”, try {topRecommendation.itemName} at {topRecommendation.toVendor} for ${topRecommendation.betterPrice.toFixed(2)}.
            </Text>
          ) : (
            <Text style={styles.assistText}>Search for items and we’ll recommend deals. At the end, set a price alert with one tap.</Text>
          )}
          <Pressable
            style={[styles.addButton, !topRecommendation && styles.addButtonDisabled]}
            onPress={setAlertFromRecommendation}
            disabled={!topRecommendation}
          >
            <Text style={styles.addButtonText}>Set Price Alert</Text>
          </Pressable>
        </View>

        <View style={styles.sectionHeaderRow}>
          <Text style={styles.sectionTitle}>Custom Price Alerts</Text>
          <Text style={styles.sectionMeta}>{priceAlerts.length} active</Text>
        </View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.carouselRow}
          nestedScrollEnabled
          directionalLockEnabled
        >
          {priceAlerts.map((alert) => {
            const gap = alert.currentBestPrice - alert.targetPrice;
            const triggerLabel = alert.triggerType === 'FUTURE_PURCHASE' ? 'Future Purchase' : 'Deal Alert';
            return (
              <View key={alert.id} style={styles.carouselCard}>
                <View style={styles.alertTop}>
                  <Text style={styles.cardTitle}>{alert.itemName}</Text>
                  <Text style={styles.alertType}>{triggerLabel}</Text>
                </View>
                <Text style={styles.cardMeta}>Target ${alert.targetPrice.toFixed(2)} · Best now ${alert.currentBestPrice.toFixed(2)}</Text>
                <Text style={styles.cardMeta}>Vendor: {alert.preferredVendor}</Text>
                <Text style={styles.alertGap}>{gap > 0 ? `$${gap.toFixed(2)} away from your target` : 'Target reached'}</Text>
                {alert.id.startsWith('wl-') ? (
                  editingAlertId === alert.id ? (
                    <View style={styles.editRow}>
                      <TextInput
                        value={editingTargetPrice}
                        onChangeText={setEditingTargetPrice}
                        keyboardType="decimal-pad"
                        style={styles.editInput}
                        placeholder="Target $"
                        placeholderTextColor="#94a3b8"
                      />
                      <Pressable style={styles.inlineAction} onPress={() => saveEdit(alert.id)}>
                        <Text style={styles.inlineActionText}>Save</Text>
                      </Pressable>
                    </View>
                  ) : (
                    <View style={styles.editRow}>
                      <Pressable style={[styles.inlineAction, styles.inlineActionGhost]} onPress={() => startEdit(alert.id, alert.targetPrice)}>
                        <Text style={[styles.inlineActionText, styles.inlineActionGhostText]}>Edit</Text>
                      </Pressable>
                    </View>
                  )
                ) : null}
              </View>
            );
          })}
        </ScrollView>

        {priceAlerts.length === 0 ? <Text style={styles.empty}>No price alerts match your search.</Text> : null}
      </ScrollView>
    </TopNavShell>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { gap: 10, paddingTop: 10, paddingBottom: 24 },
  heroCard: { backgroundColor: '#0f172a', borderRadius: 12, padding: 12 },
  heroLabel: { color: '#cbd5e1', fontSize: 12 },
  heroValue: { color: '#ffffff', fontSize: 26, fontWeight: '800', marginTop: 2 },
  heroMeta: { color: '#93c5fd', marginTop: 3, fontWeight: '600', fontSize: 12 },
  kpiRow: { flexDirection: 'row', gap: 8 },
  kpiCard: { flex: 1, backgroundColor: '#fff', borderRadius: 12, padding: 10, borderWidth: 1, borderColor: '#e2e8f0' },
  kpiTitle: { color: '#64748b', fontSize: 11, fontWeight: '600' },
  kpiValue: { color: '#0f172a', fontSize: 20, fontWeight: '800', marginTop: 2 },
  carouselRow: { gap: 8, paddingRight: 12 },
  carouselCard: {
    width: 220,
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 10,
    borderWidth: 1,
    borderColor: '#dbeafe',
  },
  sectionHeaderRow: { marginTop: 4, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sectionTitle: { color: '#0f172a', fontSize: 14, fontWeight: '800' },
  sectionMeta: { color: '#64748b', fontSize: 11, fontWeight: '600' },
  alertTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  cardTitle: { flex: 1, color: '#0f172a', fontSize: 14, fontWeight: '800' },
  cardMeta: { marginTop: 3, color: '#64748b', fontSize: 12 },
  alertType: { color: '#0f766e', fontSize: 11, fontWeight: '700' },
  alertGap: { marginTop: 4, color: '#0f172a', fontSize: 11, fontWeight: '700' },
  empty: { marginTop: 10, textAlign: 'center', color: '#64748b' },
  formCard: { backgroundColor: '#fff', borderRadius: 12, borderWidth: 1, borderColor: '#e2e8f0', padding: 10 },
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
    backgroundColor: '#fff',
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
});
