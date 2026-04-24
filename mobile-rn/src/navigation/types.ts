export type RootTabParamList = {
  Dashboard: undefined;
  Receipts: undefined;
  Inventory: undefined;
  Deals: undefined;
  Savings: undefined;
  LifeEvents: undefined;
};

export type ReceiptsStackParamList = {
  ReceiptsHome: undefined;
  Scan: { receiptId?: string } | undefined;
  ReceiptDetails: { receiptId: string };
};

export type InventoryStackParamList = {
  InventoryHome: undefined;
  InventoryItemDetails: { itemId: string };
};
