declare module 'react-native' {
  interface NativeModulesStatic {
    FoundationReceiptModel?: {
      classifyReceipt: (imageUri: string) => Promise<{
        force?: boolean;
        model_version: string;
        latency_ms: number;
        classification: {
          is_receipt: boolean;
          confidence: number;
          reason_codes: string[];
        };
        extraction: {
          merchant: string | null;
          date: string | null;
          subtotal: number | null;
          tax: number | null;
          total: number | null;
          items: Array<{
            name: string;
            qty: number;
            unit_price: number;
            line_total: number;
            confidence: {
              name: number;
              qty: number;
              unit_price: number;
              line_total: number;
            };
          }>;
          sensitive: Record<string, string>;
        };
      }>;
    };
  }
}

export {};

