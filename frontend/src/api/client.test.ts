import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import { keycloak } from '../test/keycloakMock';
import { ApiError, api, errorMessage } from './client';

// jsdom has no fetch, so each test hands the client a fake one that returns a hand-made response
type FakeResponse = { ok: boolean; status: number; text: () => Promise<string>; json: () => Promise<unknown> };
function reply(status: number, body?: unknown): FakeResponse {
  const text = body === undefined ? '' : typeof body === 'string' ? body : JSON.stringify(body);
  return { ok: status >= 200 && status < 300, status, text: async () => text, json: async () => JSON.parse(text) };
}
const fetchMock = jest.fn<(url: string, init: RequestInit) => Promise<FakeResponse>>();

beforeEach(() => {
  fetchMock.mockReset();
  (globalThis as { fetch: unknown }).fetch = fetchMock;
});

describe('api()', () => {
  it('sends the bearer token and builds the query string without empty values', async () => {
    fetchMock.mockResolvedValue(reply(200, { ok: true }));
    await api('/products', { query: { q: 'chair', page: 0, categoryId: '', active: undefined } });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/products?q=chair&page=0');
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
  });

  it('sends JSON bodies with a JSON content type', async () => {
    fetchMock.mockResolvedValue(reply(200, { id: '1' }));
    await api('/categories', { method: 'POST', body: { name: 'Chairs' } });
    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"name":"Chairs"}');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('sends a file upload as it is, without forcing a Content-Type', async () => {
    fetchMock.mockResolvedValue(reply(200, { id: '1' }));
    const form = new FormData();
    form.append('file', new File(['x'], 'a.png', { type: 'image/png' }));
    await api('/products/1/images', { method: 'POST', body: form });
    const init = fetchMock.mock.calls[0][1];
    expect(init.body).toBe(form);
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined();
  });

  it('returns undefined for an empty 204 answer', async () => {
    fetchMock.mockResolvedValue(reply(204));
    await expect(api('/products/1/images/2', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('turns an error answer into an ApiError with code, message and field errors', async () => {
    fetchMock.mockResolvedValue(reply(409, { code: 'VERSION_CONFLICT', detail: 'Changed by someone else', traceId: 't-1', errors: [{ field: 'name', message: 'is required' }] }));
    const err = await api('/products/1', { method: 'PUT', body: {} }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    const e = err as ApiError;
    expect([e.status, e.code, e.message, e.traceId]).toEqual([409, 'VERSION_CONFLICT', 'Changed by someone else', 't-1']);
    expect(e.fieldErrors).toEqual([{ field: 'name', message: 'is required' }]);
  });

  it('uses a default message when the error body is not JSON', async () => {
    fetchMock.mockResolvedValue(reply(502, 'Bad gateway <html>'));
    const e = (await api('/x').catch((x: unknown) => x)) as ApiError;
    expect([e.status, e.code]).toEqual([502, 'HTTP_502']);
    expect(e.message).toBe('The server had a problem. Please try again.');
  });

  it('refreshes the token and retries once after a 401', async () => {
    fetchMock.mockResolvedValueOnce(reply(401)).mockResolvedValueOnce(reply(200, { ok: 1 }));
    await expect(api('/orders')).resolves.toEqual({ ok: 1 });
    expect(keycloak.updateToken).toHaveBeenCalledWith(-1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('gives up after the retry if it is still 401', async () => {
    fetchMock.mockResolvedValue(reply(401));
    const e = (await api('/orders').catch((x: unknown) => x)) as ApiError;
    expect(e.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe('errorMessage()', () => {
  it('adds field errors to an ApiError message', () => {
    const e = new ApiError(400, 'VALIDATION', 'Invalid request', [{ field: 'sku', message: 'is taken' }]);
    expect(errorMessage(e)).toBe('Invalid request: sku is taken');
  });
  it('explains a network failure (fetch throws TypeError)', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toMatch(/Cannot reach the server/);
  });
  it('handles plain errors and unknown values', () => {
    expect(errorMessage(new Error('boom'))).toBe('boom');
    expect(errorMessage('weird')).toBe('Something went wrong.');
  });
});
