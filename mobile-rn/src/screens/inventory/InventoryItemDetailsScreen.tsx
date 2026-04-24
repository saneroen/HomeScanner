import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import React from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useDeleteInventoryItemMutation, useInventoryEventsQuery, useInventoryItemQuery } from '../../api/inventory';
import { InventoryStackParamList } from '../../navigation/types';

type Props = NativeStackScreenProps<InventoryStackParamList, 'InventoryItemDetails'>;

export function InventoryItemDetailsScreen({ navigation, route }: Props) {
  const { itemId } = route.params;

  const itemQuery = useInventoryItemQuery(itemId);
  const eventsQuery = useInventoryEventsQuery(itemId);
  const deleteItem = useDeleteInventoryItemMutation();

  if (itemQuery.isLoading) {
    return (
      <View style={styles.loaderWrap}>
        <ActivityIndicator size="small" />
      </View>
    );
  }

  if (!itemQuery.data) {
    return (
      <View style={styles.loaderWrap}>
        <Text style={styles.empty}>Inventory item not found.</Text>
      </View>
    );
  }

  const item = itemQuery.data;
  const events = eventsQuery.data ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.navRow}>
        <Pressable style={styles.backButton} onPress={() => navigation.goBack()}>
          <Ionicons name="chevron-back" size={16} color="#0f172a" />
          <Text style={styles.backText}>Back</Text>
        </Pressable>
      </View>

      <View style={styles.card}>
        <Text style={styles.title}>{item.display_name}</Text>
        <Text style={styles.meta}>Category: {item.category}</Text>
        <Text style={styles.meta}>Purchase count: {item.purchase_count}</Text>
        <Text style={styles.meta}>Last price: {item.last_purchase_price != null ? `$${item.last_purchase_price.toFixed(2)}` : 'N/A'}</Text>
        <Text style={styles.meta}>Updated: {new Date(item.updated_at).toLocaleString()}</Text>
        <Pressable
          style={[styles.actionButton, styles.deleteButton]}
          onPress={() => {
            Alert.alert('Delete inventory item?', 'This will remove this item and linked history from inventory.', [
              { text: 'Cancel', style: 'cancel' },
              {
                text: 'Delete',
                style: 'destructive',
                onPress: async () => {
                  await deleteItem.mutateAsync(item.id);
                  navigation.goBack();
                },
              },
            ]);
          }}
        >
          <Text style={styles.actionButtonText}>Delete Item</Text>
        </Pressable>
      </View>

      <Text style={styles.sectionTitle}>Purchase Events</Text>

      {eventsQuery.isLoading ? <ActivityIndicator size="small" /> : null}

      {events.map((event) => (
        <View style={styles.card} key={event.id}>
          <Text style={styles.eventTitle}>Qty {event.quantity}</Text>
          <Text style={styles.meta}>Unit: ${event.unit_price.toFixed(2)}</Text>
          <Text style={styles.meta}>Line total: ${event.line_total.toFixed(2)}</Text>
          <Text style={styles.meta}>Occurred: {new Date(event.occurred_at).toLocaleString()}</Text>
          <Text style={styles.meta}>Receipt: {event.receipt_id ?? 'N/A'}</Text>
        </View>
      ))}

      {!eventsQuery.isLoading && events.length === 0 ? <Text style={styles.empty}>No purchase events yet.</Text> : null}
    </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f8fafc' },
  container: { flex: 1, backgroundColor: '#f8fafc' },
  content: { padding: 12, paddingBottom: 24, gap: 8 },
  loaderWrap: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  navRow: { paddingTop: 4, marginBottom: 4 },
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
  card: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    backgroundColor: '#fff',
    padding: 12,
  },
  title: { fontSize: 20, fontWeight: '700', color: '#0f172a' },
  sectionTitle: { marginTop: 4, marginBottom: 2, fontSize: 15, fontWeight: '700', color: '#0f172a' },
  eventTitle: { fontSize: 14, fontWeight: '700', color: '#111827' },
  meta: { marginTop: 4, color: '#64748b', fontSize: 12 },
  empty: { color: '#64748b' },
  actionButton: {
    marginTop: 12,
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: 'center',
  },
  deleteButton: {
    backgroundColor: '#fee2e2',
    borderColor: '#ef4444',
    borderWidth: 1,
  },
  actionButtonText: {
    color: '#b91c1c',
    fontWeight: '700',
    fontSize: 13,
  },
});
