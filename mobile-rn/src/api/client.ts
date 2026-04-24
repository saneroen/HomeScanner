import axios from 'axios';

import { ENV } from '../config/env';

export const api = axios.create({
  baseURL: ENV.API_BASE_URL.replace(/\/$/, ''),
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export type HealthResponse = {
  status: string;
  env: string;
};

export const getHealth = async (): Promise<HealthResponse> => {
  const response = await api.get<HealthResponse>('/health');
  return response.data;
};

