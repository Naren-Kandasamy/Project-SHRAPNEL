import axios from 'axios';

// Connects to your Spring Boot default port
const API_BASE_URL = 'http://localhost:8080/api/SHRAPNEL';

export const apiClient = axios.create({
    baseURL: API_BASE_URL
});

export const fetchFiles = async () => {
    const response = await apiClient.get('/files');
    return response.data;
};

export const startRestore = async (fileId: string, password?: string) => {
    let url = `/restore/start/${fileId}`;
    if (password) {
        url += `?password=${encodeURIComponent(password)}`;
    }
    await apiClient.post(url);
};

export const fetchRestoreStatus = async (fileId: string) => {
    const response = await apiClient.get(`/restore/status/${fileId}`);
    return response.data; // integer
};