/// <reference types="node" />
export declare const httpClient: {
    fetch: typeof fetch;
};
/**
 * Issue an HTTP request with digest authentication.
 * Performs the two-step challenge/response dance: the first request
 * collects the `WWW-Authenticate` challenge, the second sends a computed
 * `Authorization` header. Response bodies are not consumed — callers get
 * the raw `Response` and inspect status/headers only.
 */
export declare function fetchWithDigest(url: string, init: RequestInit & {
    method: string;
    username: string;
    password: string;
    timeout: number;
}): Promise<Response>;
export declare function parseDigestChallenge(header: string): Record<string, string>;
export declare function buildDigestAuthorization(params: {
    username: string;
    password: string;
    method: string;
    uri: string;
    challenge: Record<string, string>;
}): string;
