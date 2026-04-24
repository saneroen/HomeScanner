import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';
import React, { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useClassifyReceiptMutation, useCreateReceiptMutation, useUploadReceiptImageMutation } from '../../api/receipts';
import { ReceiptsStackParamList } from '../../navigation/types';

type Props = NativeStackScreenProps<ReceiptsStackParamList, 'Scan'>;

export function ScanScreen({ navigation, route }: Props) {
  const [busy, setBusy] = useState(false);
  const createReceipt = useCreateReceiptMutation();
  const uploadReceiptImage = useUploadReceiptImageMutation();
  const classifyReceipt = useClassifyReceiptMutation();

  const upsertReceiptFromAsset = async (asset: ImagePicker.ImagePickerAsset) => {
    const uri = asset.uri;
    const receiptId = route.params?.receiptId ?? (await createReceipt.mutateAsync(undefined)).id;
    const fileName = asset.fileName ?? `receipt-${Date.now()}.jpg`;
    const looksLikeHeic = /\.(heic|heif)$/i.test(fileName) || /image\/(heic|heif)/i.test(asset.mimeType ?? '');

    await uploadReceiptImage.mutateAsync({
      receiptId,
      uri,
      fileName: looksLikeHeic ? fileName.replace(/\.(heic|heif)$/i, '.jpg') : fileName,
      mimeType: looksLikeHeic ? 'image/jpeg' : asset.mimeType ?? 'image/jpeg',
    });

    await classifyReceipt.mutateAsync({ receiptId });
    navigation.navigate('ReceiptsHome');
  };

  const runCameraCapture = async () => {
    setBusy(true);
    try {
      const permission = await ImagePicker.requestCameraPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Camera permission required', 'Enable camera access to scan receipt images.');
        return;
      }

      const result = await ImagePicker.launchCameraAsync({
        allowsEditing: false,
        quality: 0.8,
        mediaTypes: ['images'],
      });

      if (result.canceled || !result.assets?.length) return;
      await upsertReceiptFromAsset(result.assets[0]);
    } finally {
      setBusy(false);
    }
  };

  const runGalleryPick = async () => {
    setBusy(true);
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Photo permission required', 'Enable photo library access to select receipt images.');
        return;
      }

      const result = await ImagePicker.launchImageLibraryAsync({
        allowsEditing: false,
        quality: 0.9,
        mediaTypes: ['images'],
        preferredAssetRepresentationMode: ImagePicker.UIImagePickerPreferredAssetRepresentationMode.Compatible,
      });

      if (result.canceled || !result.assets?.length) return;
      await upsertReceiptFromAsset(result.assets[0]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
    <View style={styles.container}>
      <View style={styles.navRow}>
        <Pressable style={styles.backButton} onPress={() => navigation.goBack()}>
          <Ionicons name="chevron-back" size={16} color="#0f172a" />
          <Text style={styles.backText}>Back</Text>
        </Pressable>
      </View>
      <View style={styles.card}>
      <Text style={styles.title}>Scan Receipt</Text>
      <Text style={styles.subtitle}>Capture or select a receipt image to start review.</Text>

      <Pressable style={styles.primaryButton} disabled={busy} onPress={runCameraCapture}>
        <Text style={styles.primaryButtonText}>Open Camera</Text>
      </Pressable>

      <Pressable style={styles.secondaryButton} disabled={busy} onPress={runGalleryPick}>
        <Text style={styles.secondaryButtonText}>Pick from Photos</Text>
      </Pressable>

      {busy ? <ActivityIndicator style={{ marginTop: 16 }} size="small" /> : null}
      </View>
    </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f8fafc' },
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
    paddingHorizontal: 12,
    paddingBottom: 12,
  },
  navRow: { paddingTop: 4, marginBottom: 8 },
  backButton: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 999,
    backgroundColor: '#fff',
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  backText: { color: '#0f172a', fontSize: 12, fontWeight: '700' },
  card: { borderRadius: 12, borderWidth: 1, borderColor: '#e2e8f0', backgroundColor: '#fff', padding: 12 },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#0f172a',
  },
  subtitle: {
    marginTop: 8,
    color: '#475569',
    fontSize: 14,
  },
  primaryButton: {
    marginTop: 24,
    backgroundColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  primaryButtonText: {
    color: '#fff',
    fontWeight: '700',
  },
  secondaryButton: {
    marginTop: 12,
    backgroundColor: '#fff',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
    borderColor: '#cbd5e1',
    borderWidth: 1,
  },
  secondaryButtonText: {
    color: '#0f172a',
    fontWeight: '700',
  },
});
