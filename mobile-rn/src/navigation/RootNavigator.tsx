import { Ionicons } from '@expo/vector-icons';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import React from 'react';

import { DashboardScreen } from '../screens/dashboard/DashboardScreen';
import { DealsChatScreen } from '../screens/deals/DealsChatScreen';
import { InventoryHomeScreen } from '../screens/inventory/InventoryHomeScreen';
import { InventoryItemDetailsScreen } from '../screens/inventory/InventoryItemDetailsScreen';
import { LifeEventsScreen } from '../screens/life-events/LifeEventsScreen';
import { ReceiptDetailsScreen } from '../screens/receipts/ReceiptDetailsScreen';
import { ReceiptsHomeScreen } from '../screens/receipts/ReceiptsHomeScreen';
import { ScanScreen } from '../screens/receipts/ScanScreen';
import { SavingsOpportunitiesScreen } from '../screens/savings/SavingsOpportunitiesScreen';
import { InventoryStackParamList, ReceiptsStackParamList, RootTabParamList } from './types';

const RootStack = createNativeStackNavigator<RootTabParamList>();
const ReceiptsStack = createNativeStackNavigator<ReceiptsStackParamList>();
const InventoryStack = createNativeStackNavigator<InventoryStackParamList>();

function ReceiptsStackNavigator() {
  return (
    <ReceiptsStack.Navigator screenOptions={{ headerShown: false }}>
      <ReceiptsStack.Screen name="ReceiptsHome" component={ReceiptsHomeScreen} options={{ title: 'Receipts' }} />
      <ReceiptsStack.Screen name="Scan" component={ScanScreen} options={{ title: 'Scan' }} />
      <ReceiptsStack.Screen name="ReceiptDetails" component={ReceiptDetailsScreen} options={{ title: 'Receipt Details' }} />
    </ReceiptsStack.Navigator>
  );
}

function InventoryStackNavigator() {
  return (
    <InventoryStack.Navigator screenOptions={{ headerShown: false }}>
      <InventoryStack.Screen name="InventoryHome" component={InventoryHomeScreen} options={{ title: 'Inventory' }} />
      <InventoryStack.Screen
        name="InventoryItemDetails"
        component={InventoryItemDetailsScreen}
        options={{ title: 'Inventory Item' }}
      />
    </InventoryStack.Navigator>
  );
}

export function RootNavigator() {
  return (
    <NavigationContainer>
      <RootStack.Navigator
        initialRouteName="Dashboard"
        screenOptions={{ headerShown: false, animation: 'none' }}
      >
        <RootStack.Screen name="Dashboard" component={DashboardScreen} />
        <RootStack.Screen name="Receipts" component={ReceiptsStackNavigator} />
        <RootStack.Screen name="Inventory" component={InventoryStackNavigator} />
        <RootStack.Screen name="Deals" component={DealsChatScreen} />
        <RootStack.Screen name="Savings" component={SavingsOpportunitiesScreen} />
        <RootStack.Screen name="LifeEvents" component={LifeEventsScreen} options={{ title: 'Life Events' }} />
      </RootStack.Navigator>
    </NavigationContainer>
  );
}
