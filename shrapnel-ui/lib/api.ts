import axios from 'axios';

// Connects to your Spring Boot default port
const API_BASE_URL = 'http://localhost:8080/api/SHRAPNEL';

// Use your default credentials from application.yaml
const authHeader = 'Basic ' + btoa('naren:123');

export const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Authorization': authHeader
    }
});

export const fetchFiles = async () => {
    const response = await apiClient.get('/files');
    return response.data;
};

export const downloadFile = (fileId: string) => {
    // For downloads, we can just redirect the browser with auth included in URL or use fetch
    window.location.href = `http://naren:123@localhost:8080/api/SHRAPNEL/download/${fileId}`;
};