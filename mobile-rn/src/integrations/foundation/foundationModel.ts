import { NativeModules, Platform } from 'react-native';

import {
  ReceiptClassifyPayload,
} from '../../api/receipts';

export type FoundationRunResult = {
  request: ReceiptClassifyPayload;
  debug: {
    provider: string;
    note?: string;
  };
};

type FoundationNativeBridge = {
  classifyReceipt: (imageUri: string) => Promise<unknown>;
};

const getFoundationBridge = (): FoundationNativeBridge => {
  if (Platform.OS !== 'ios') {
    throw new Error('Foundation model runtime is iOS-only in current implementation.');
  }

  const bridge = (NativeModules as Record<string, unknown>).FoundationReceiptModel as
    | FoundationNativeBridge
    | undefined;
  if (!bridge?.classifyReceipt) {
    throw new Error(
      'FoundationReceiptModel native bridge is not configured. Implement classifyReceipt(imageUri) on iOS.',
    );
  }
  return bridge;
};

const assertPayload = (value: unknown): ReceiptClassifyPayload => {
  if (!value || typeof value !== 'object') {
    throw new Error('Invalid Foundation response: expected object payload.');
  }

  const payload = value as Partial<ReceiptClassifyPayload>;
  if (
    !payload.model_version ||
    typeof payload.model_version !== 'string' ||
    typeof payload.latency_ms !== 'number' ||
    !payload.classification ||
    !payload.extraction
  ) {
    throw new Error('Invalid Foundation response: missing required classify fields.');
  }

  return payload as ReceiptClassifyPayload;
};

export const runFoundationModelOnImage = async (imageUri: string): Promise<FoundationRunResult> => {
  const bridge = getFoundationBridge();
  const nativeResponse = await bridge.classifyReceipt(imageUri);
  const request = assertPayload(nativeResponse);

  return {
    request,
    debug: {
      provider: 'foundation-native',
    },
  };
};
