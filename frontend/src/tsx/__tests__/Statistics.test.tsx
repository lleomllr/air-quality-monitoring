/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, type Mock } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import Statistics from '../Statistics';

// Interface pour typer nos données API
interface AqiData {
    city: string;
    state: string;
    pollutant: string;
    aqi: number;
    timeStart: string;
}

const localStorageMock = (() => {
    let store: Record<string, string> = {};
    return {
        getItem: vi.fn((key: string) => store[key] || null),
        setItem: vi.fn((key: string, value: string) => { store[key] = value.toString(); }),
        removeItem: vi.fn((key: string) => { delete store[key]; }),
        clear: vi.fn(() => { store = {}; }),
    };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

//On utilise globalThis au lieu de global
globalThis.fetch = vi.fn() as Mock;

describe('Statistics Component', () => {

    beforeEach(() => {
        vi.clearAllMocks();
        localStorageMock.clear();
    });

    it('should display the loading message on first render', () => {
        // On utilise globalThis
        (globalThis.fetch as Mock).mockImplementation(() => new Promise(() => {}));

        render(
            <BrowserRouter>
                <Statistics />
            </BrowserRouter>
        );

        const loadingElement = screen.queryByText(/Loading/i);
        expect(loadingElement).not.toBeNull();
    });

    it('should display pollution data once loaded', async () => {
        const mockData: AqiData[] = [
            {
                city: 'Phoenix',
                state: 'AZ',
                pollutant: 'PM10',
                aqi: 150,
                timeStart: '2026-03-04T10:00:00Z'
            }
        ];

        // On utilise globalThis
        (globalThis.fetch as Mock).mockImplementation(() => Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockData),
        }));

        render(
            <BrowserRouter>
                <Statistics />
            </BrowserRouter>
        );

        const cityElements = await screen.findAllByText(/Phoenix/i);

        expect(cityElements.length).toBeGreaterThan(0);
        expect(screen.queryAllByText(/PM10/i).length).toBeGreaterThan(0);
        expect(screen.queryAllByText(/150/i).length).toBeGreaterThan(0);
    });
});