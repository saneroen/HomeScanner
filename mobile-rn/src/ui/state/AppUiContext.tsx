import React, { createContext, ReactNode, useContext, useMemo, useState } from 'react';

import { RootTabParamList } from '../../navigation/types';

type SearchScope = 'dashboard' | 'deals' | 'savings' | 'lifeEvents' | 'inventory' | 'receipts';

type SearchMap = Record<SearchScope, string>;

export type WatchlistAlert = {
  id: string;
  item: string;
  targetPrice: number;
  currentBest: number;
  vendor: string;
  category: 'Grocery' | 'Furniture' | 'Clothing' | 'Electronics' | 'Other';
};

export type TopNavCategory = {
  id: string;
  label: string;
  icon: keyof typeof import('@expo/vector-icons').Ionicons.glyphMap;
  routeName: keyof RootTabParamList;
};

type AppUiContextValue = {
  regionOptions: string[];
  region: string;
  setRegion: (next: string) => void;
  cycleRegion: () => void;
  topNavCategories: TopNavCategory[];
  activeTopNavCategoryId: string;
  setActiveTopNavCategoryId: (id: string) => void;
  searches: SearchMap;
  setSearch: (scope: SearchScope, value: string) => void;
  watchlistAlerts: WatchlistAlert[];
  addWatchlistAlert: (payload: {
    item: string;
    targetPrice: number;
    vendor: string;
    category: WatchlistAlert['category'];
  }) => void;
  updateWatchlistAlert: (
    id: string,
    payload: Partial<Pick<WatchlistAlert, 'item' | 'targetPrice' | 'vendor' | 'category'>>,
  ) => void;
};

const REGION_OPTIONS = ['94107', '10001', '60601', '98101'];

const TOP_NAV_CATEGORIES: TopNavCategory[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'grid-outline', routeName: 'Dashboard' },
  { id: 'receipts', label: 'Receipts', icon: 'receipt-outline', routeName: 'Receipts' },
  { id: 'inventory', label: 'Inventory', icon: 'cube-outline', routeName: 'Inventory' },
  { id: 'deals', label: 'Deals', icon: 'chatbubble-ellipses-outline', routeName: 'Deals' },
  { id: 'savings', label: 'Savings', icon: 'wallet-outline', routeName: 'Savings' },
  { id: 'life-events', label: 'Life Events', icon: 'sparkles-outline', routeName: 'LifeEvents' },
];

const INITIAL_WATCHLIST_ALERTS: WatchlistAlert[] = [
  { id: 'w1', item: 'Organic Eggs', targetPrice: 4.99, currentBest: 5.49, vendor: 'Costco', category: 'Grocery' },
  { id: 'w2', item: 'Laundry Pods', targetPrice: 14.99, currentBest: 16.99, vendor: 'Amazon', category: 'Other' },
  { id: 'w3', item: 'Kids T-Shirts', targetPrice: 8.0, currentBest: 8.5, vendor: 'Old Navy', category: 'Clothing' },
];

const AppUiContext = createContext<AppUiContextValue | null>(null);

export function AppUiProvider({ children }: { children: ReactNode }) {
  const [region, setRegion] = useState<string>(REGION_OPTIONS[0]);
  const [activeTopNavCategoryId, setActiveTopNavCategoryId] = useState<string>(TOP_NAV_CATEGORIES[0].id);
  const [watchlistAlerts, setWatchlistAlerts] = useState<WatchlistAlert[]>(INITIAL_WATCHLIST_ALERTS);
  const [searches, setSearches] = useState<SearchMap>({
    dashboard: '',
    deals: '',
    savings: '',
    lifeEvents: '',
    inventory: '',
    receipts: '',
  });

  const cycleRegion = () => {
    const index = REGION_OPTIONS.indexOf(region);
    const nextIndex = index >= 0 ? (index + 1) % REGION_OPTIONS.length : 0;
    setRegion(REGION_OPTIONS[nextIndex]);
  };

  const setSearch = (scope: SearchScope, value: string) => {
    setSearches((prev) => ({ ...prev, [scope]: value }));
  };

  const addWatchlistAlert: AppUiContextValue['addWatchlistAlert'] = ({ item, targetPrice, vendor, category }) => {
    const cleanItem = item.trim();
    if (!cleanItem) return;
    const safeTarget = Number.isFinite(targetPrice) ? Math.max(0, targetPrice) : 0;
    const generatedId = `w-${Date.now()}`;
    setWatchlistAlerts((prev) => [
      {
        id: generatedId,
        item: cleanItem,
        targetPrice: safeTarget,
        currentBest: Number((safeTarget * 1.12).toFixed(2)),
        vendor: vendor.trim() || 'Unknown',
        category,
      },
      ...prev,
    ]);
  };

  const updateWatchlistAlert: AppUiContextValue['updateWatchlistAlert'] = (id, payload) => {
    setWatchlistAlerts((prev) =>
      prev.map((entry) => {
        if (entry.id !== id) return entry;
        const nextItem = payload.item?.trim() ? payload.item.trim() : entry.item;
        const nextVendor = payload.vendor?.trim() ? payload.vendor.trim() : entry.vendor;
        const nextTargetRaw = payload.targetPrice;
        const nextTarget = Number.isFinite(nextTargetRaw as number)
          ? Math.max(0, nextTargetRaw as number)
          : entry.targetPrice;
        return {
          ...entry,
          item: nextItem,
          vendor: nextVendor,
          category: payload.category ?? entry.category,
          targetPrice: nextTarget,
        };
      }),
    );
  };

  const value = useMemo<AppUiContextValue>(
    () => ({
      regionOptions: REGION_OPTIONS,
      region,
      setRegion,
      cycleRegion,
      topNavCategories: TOP_NAV_CATEGORIES,
      activeTopNavCategoryId,
      setActiveTopNavCategoryId,
      searches,
      setSearch,
      watchlistAlerts,
      addWatchlistAlert,
      updateWatchlistAlert,
    }),
    [activeTopNavCategoryId, region, searches, watchlistAlerts],
  );

  return <AppUiContext.Provider value={value}>{children}</AppUiContext.Provider>;
}

export function useAppUi() {
  const value = useContext(AppUiContext);
  if (!value) {
    throw new Error('useAppUi must be used inside AppUiProvider');
  }
  return value;
}
