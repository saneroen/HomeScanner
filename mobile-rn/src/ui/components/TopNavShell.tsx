import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import React, { ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAppUi } from '../state/AppUiContext';

type Props = {
  regionLabel: string;
  onRegionPress?: () => void;
  searchPlaceholder: string;
  searchValue: string;
  onSearchChange: (value: string) => void;
  children: ReactNode;
};

export function TopNavShell({
  regionLabel,
  onRegionPress,
  searchPlaceholder,
  searchValue,
  onSearchChange,
  children,
}: Props) {
  const { topNavCategories, activeTopNavCategoryId, setActiveTopNavCategoryId } = useAppUi();
  const navigation = useNavigation();
  const route = useRoute();

  const resolveRouteName = () => {
    if (route.name === 'ReceiptsHome') return 'Receipts';
    if (route.name === 'InventoryHome') return 'Inventory';
    if (route.name === 'Dashboard') return 'Dashboard';
    if (route.name === 'Deals') return 'Deals';
    if (route.name === 'Savings') return 'Savings';
    if (route.name === 'LifeEvents') return 'LifeEvents';
    return null;
  };

  const currentRootRouteName = resolveRouteName();

  const handleTopCategoryPress = (id: string, routeName: string) => {
    setActiveTopNavCategoryId(id);

    let currentNav: any = navigation;
    while (currentNav?.getParent?.()) {
      currentNav = currentNav.getParent();
    }

    currentNav?.navigate?.(routeName);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.topBlock}>
        <View style={styles.topRow}>
          <Pressable style={styles.homeWrap} onPress={onRegionPress}>
            <Text style={styles.homeText}>Home</Text>
            <Ionicons name="chevron-down" size={24} color="#111827" />
          </Pressable>

          <View style={styles.quickIcons}>
            <Pressable style={styles.actionCircle}>
              <Ionicons name="person-outline" size={29} color="#111827" />
            </Pressable>
            <Pressable style={styles.actionCircle}>
              <Ionicons name="notifications-outline" size={29} color="#111827" />
              <View style={styles.badgeDot} />
            </Pressable>
          </View>
        </View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.categoriesScroll}
          contentContainerStyle={styles.categoriesRow}
          nestedScrollEnabled
          directionalLockEnabled
        >
          {topNavCategories.map((item) => {
            const active = item.routeName === currentRootRouteName || item.id === activeTopNavCategoryId;
            return (
              <Pressable
                key={item.id}
                style={styles.categoryItem}
                onPress={() => handleTopCategoryPress(item.id, item.routeName)}
              >
                <Ionicons name={item.icon} size={28} color={active ? '#0f172a' : '#1f2937'} />
                <Text style={[styles.categoryLabel, active && styles.categoryLabelActive]}>{item.label}</Text>
              </Pressable>
            );
          })}
        </ScrollView>

        <View style={styles.divider} />

        <View style={styles.searchPanel}>
          <View style={styles.searchWrap}>
            <Ionicons name="search-outline" size={36} color="#111827" />
            <TextInput
              placeholder={searchPlaceholder}
              placeholderTextColor="#6b7280"
              style={styles.searchInput}
              value={searchValue}
              onChangeText={onSearchChange}
            />
          </View>
        </View>
      </View>

      <View style={styles.content}>
        {children}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f3f4f6' },
  topBlock: { paddingHorizontal: 14, paddingTop: 6, paddingBottom: 6 },
  content: { flex: 1, paddingHorizontal: 16 },
  topRow: { marginTop: 8, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  homeWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  homeText: { color: '#111827', fontWeight: '700', fontSize: 20 },
  quickIcons: { flexDirection: 'row', gap: 8 },
  actionCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#e8eaef',
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
  },
  badgeDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#ef3b2d',
    position: 'absolute',
    top: 5,
    right: 6,
  },
  categoriesScroll: { marginTop: 8, maxHeight: 74 },
  categoriesRow: { gap: 10, paddingRight: 16 },
  categoryItem: {
    minWidth: 52,
    paddingHorizontal: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  categoryLabel: {
    marginTop: 2,
    color: '#202733',
    fontSize: 13,
    fontWeight: '500',
  },
  categoryLabelActive: { fontWeight: '700', color: '#0f172a' },
  divider: {
    marginTop: 10,
    height: 1,
    backgroundColor: '#d7dae0',
  },
  searchPanel: {
    marginTop: 10,
    borderRadius: 16,
    backgroundColor: '#c5b6ee',
    paddingHorizontal: 10,
    paddingVertical: 10,
  },
  searchWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: '#fff',
    borderColor: '#d8dce2',
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  searchInput: { flex: 1, color: '#111827', fontSize: 15, fontWeight: '600', paddingVertical: 0 },
});
