export type MonthlySavingsSummary = {
  monthLabel: string;
  totalSavings: number;
  potentialSavings: number;
  vendorShiftCount: number;
  activeDealAlerts: number;
};

export type VendorShiftSuggestion = {
  id: string;
  itemName: string;
  fromVendor: string;
  toVendor: string;
  currentPrice: number;
  betterPrice: number;
  bestDay: string;
  monthlyImpact: number;
};

export type FrequentItemSaving = {
  id: string;
  itemName: string;
  category: string;
  frequencyPerMonth: number;
  avgPaidPrice: number;
  bestDealPrice: number;
  bestVendor: string;
  savingsPerMonth: number;
};

export type InventoryPurchaseRow = {
  id: string;
  itemName: string;
  category: string;
  vendor: string;
  purchaseDate: string;
  paidPrice: number;
  currentBestPrice: number;
};

export type LifeEventTemplate = {
  id: string;
  title: string;
  description: string;
  trackedCategories: string[];
  trackedVendors: string[];
};

export type PriceAlert = {
  id: string;
  itemName: string;
  targetPrice: number;
  currentBestPrice: number;
  preferredVendor: string;
  triggerType: 'FUTURE_PURCHASE' | 'DEAL_ALERT';
};

export const monthlySavingsSummary: MonthlySavingsSummary = {
  monthLabel: 'April 2026',
  totalSavings: 186.4,
  potentialSavings: 312.55,
  vendorShiftCount: 7,
  activeDealAlerts: 14,
};

export const vendorShiftSuggestions: VendorShiftSuggestion[] = [
  {
    id: 'shift-1',
    itemName: 'Organic Eggs (12ct)',
    fromVendor: 'Whole Foods',
    toVendor: 'Costco',
    currentPrice: 6.49,
    betterPrice: 4.99,
    bestDay: 'Saturday',
    monthlyImpact: 6,
  },
  {
    id: 'shift-2',
    itemName: 'Dishwasher Pods',
    fromVendor: 'Target',
    toVendor: 'Amazon',
    currentPrice: 21.99,
    betterPrice: 16.99,
    bestDay: 'Tuesday',
    monthlyImpact: 10,
  },
  {
    id: 'shift-3',
    itemName: 'Protein Yogurt Pack',
    fromVendor: 'Safeway',
    toVendor: 'Trader Joe\'s',
    currentPrice: 9.5,
    betterPrice: 7.25,
    bestDay: 'Wednesday',
    monthlyImpact: 9,
  },
];

export const frequentItemSavings: FrequentItemSaving[] = [
  {
    id: 'freq-1',
    itemName: 'Bananas',
    category: 'Food',
    frequencyPerMonth: 6,
    avgPaidPrice: 1.29,
    bestDealPrice: 0.89,
    bestVendor: 'Aldi',
    savingsPerMonth: 2.4,
  },
  {
    id: 'freq-2',
    itemName: 'Laundry Detergent',
    category: 'Grocery',
    frequencyPerMonth: 2,
    avgPaidPrice: 18.99,
    bestDealPrice: 13.99,
    bestVendor: 'Costco',
    savingsPerMonth: 10,
  },
  {
    id: 'freq-3',
    itemName: 'Office Chair Wipes',
    category: 'Furniture',
    frequencyPerMonth: 1,
    avgPaidPrice: 14.99,
    bestDealPrice: 9.99,
    bestVendor: 'Walmart',
    savingsPerMonth: 5,
  },
  {
    id: 'freq-4',
    itemName: 'Kids T-Shirts',
    category: 'Clothing',
    frequencyPerMonth: 2,
    avgPaidPrice: 12.5,
    bestDealPrice: 8.0,
    bestVendor: 'Old Navy',
    savingsPerMonth: 9,
  },
];

export const inventoryMonthlyRows: InventoryPurchaseRow[] = [
  {
    id: 'inv-1',
    itemName: 'Milk 2%',
    category: 'Grocery',
    vendor: 'Trader Joe\'s',
    purchaseDate: '2026-04-03',
    paidPrice: 3.99,
    currentBestPrice: 3.49,
  },
  {
    id: 'inv-2',
    itemName: 'Chicken Breast 2lb',
    category: 'Food',
    vendor: 'Whole Foods',
    purchaseDate: '2026-04-05',
    paidPrice: 14.2,
    currentBestPrice: 11.9,
  },
  {
    id: 'inv-3',
    itemName: 'Desk Lamp',
    category: 'Furniture',
    vendor: 'IKEA',
    purchaseDate: '2026-04-08',
    paidPrice: 32,
    currentBestPrice: 28,
  },
  {
    id: 'inv-4',
    itemName: 'Running Shorts',
    category: 'Clothing',
    vendor: 'Nike',
    purchaseDate: '2026-04-14',
    paidPrice: 45,
    currentBestPrice: 34,
  },
  {
    id: 'inv-5',
    itemName: 'USB-C Hub',
    category: 'Online Purchases',
    vendor: 'Amazon',
    purchaseDate: '2026-04-16',
    paidPrice: 29.99,
    currentBestPrice: 22.99,
  },
];

export const lifeEventTemplates: LifeEventTemplate[] = [
  {
    id: 'life-1',
    title: 'Moving to a New Home',
    description: 'Track furniture, home essentials, and setup deals over 8 weeks.',
    trackedCategories: ['Furniture', 'Grocery', 'Home Essentials'],
    trackedVendors: ['IKEA', 'Costco', 'Target', 'Amazon'],
  },
  {
    id: 'life-2',
    title: 'New Baby',
    description: 'Monitor diapers, formula, wipes, and recurring family bundle discounts.',
    trackedCategories: ['Baby Care', 'Grocery', 'Pharmacy'],
    trackedVendors: ['Costco', 'Walmart', 'CVS'],
  },
  {
    id: 'life-3',
    title: 'Back to School',
    description: 'Watch uniforms, stationery, lunch items, and electronics deals.',
    trackedCategories: ['Clothing', 'School Supplies', 'Electronics'],
    trackedVendors: ['Target', 'Best Buy', 'Old Navy'],
  },
];

export const userPriceAlerts: PriceAlert[] = [
  {
    id: 'alert-1',
    itemName: 'Ergonomic Office Chair',
    targetPrice: 220,
    currentBestPrice: 279,
    preferredVendor: 'Amazon',
    triggerType: 'FUTURE_PURCHASE',
  },
  {
    id: 'alert-2',
    itemName: 'Used Sedan (2019+)',
    targetPrice: 16500,
    currentBestPrice: 17990,
    preferredVendor: 'CarMax',
    triggerType: 'FUTURE_PURCHASE',
  },
  {
    id: 'alert-3',
    itemName: 'Robot Vacuum',
    targetPrice: 299,
    currentBestPrice: 329,
    preferredVendor: 'Costco',
    triggerType: 'DEAL_ALERT',
  },
  {
    id: 'alert-4',
    itemName: '4K Monitor 32"',
    targetPrice: 260,
    currentBestPrice: 284,
    preferredVendor: 'Best Buy',
    triggerType: 'DEAL_ALERT',
  },
];

const REGION_VENDOR_ALLOWLIST: Record<string, string[]> = {
  '94107': ['Costco', 'Trader Joe\'s', 'Amazon', 'Target', 'Whole Foods', 'Safeway', 'IKEA', 'Nike', 'Aldi'],
  '10001': ['Amazon', 'Target', 'Costco', 'CVS', 'Walmart', 'Old Navy', 'Best Buy', 'Whole Foods'],
  '60601': ['Walmart', 'Costco', 'Amazon', 'Target', 'Trader Joe\'s', 'CVS', 'IKEA', 'Old Navy'],
  '98101': ['Amazon', 'Costco', 'Target', 'Safeway', 'Trader Joe\'s', 'IKEA', 'Best Buy', 'Nike'],
};

export const regionVendors = (regionZip: string): string[] => {
  return REGION_VENDOR_ALLOWLIST[regionZip] ?? REGION_VENDOR_ALLOWLIST['94107'];
};

export const vendorAvailableInRegion = (regionZip: string, vendor: string): boolean => {
  return regionVendors(regionZip).includes(vendor);
};
