import { useQuery } from '@tanstack/react-query';

import { getHealth } from './client';

export const useHealthQuery = () =>
  useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    retry: 1,
  });

