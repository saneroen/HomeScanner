import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { RootNavigator } from './src/navigation/RootNavigator';
import { AppUiProvider } from './src/ui/state/AppUiContext';

export default function App() {
  const queryClient = useMemo(() => new QueryClient(), []);

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AppUiProvider>
          <RootNavigator />
        </AppUiProvider>
        <StatusBar style="auto" />
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
