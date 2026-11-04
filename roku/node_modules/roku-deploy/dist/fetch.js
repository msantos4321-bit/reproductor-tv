"use strict";
var __rest = (this && this.__rest) || function (s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
};
var _a;
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildDigestAuthorization = exports.parseDigestChallenge = exports.fetchWithDigest = exports.httpClient = void 0;
const crypto = require("crypto");
// Module seam for `fetch` so tests can stub it. On Node 18, `fetch` is a lazy
// getter on `globalThis` (not an own property), so `sinon.stub(globalThis, 'fetch')`
// fails there — routing calls through this object gives a regular, stubbable export.
exports.httpClient = {
    fetch: (_a = globalThis.fetch) === null || _a === void 0 ? void 0 : _a.bind(globalThis)
};
/**
 * Issue an HTTP request with digest authentication.
 * Performs the two-step challenge/response dance: the first request
 * collects the `WWW-Authenticate` challenge, the second sends a computed
 * `Authorization` header. Response bodies are not consumed — callers get
 * the raw `Response` and inspect status/headers only.
 */
async function fetchWithDigest(url, init) {
    const { username, password, timeout } = init, fetchInit = __rest(init, ["username", "password", "timeout"]);
    const method = fetchInit.method.toUpperCase();
    // Step 1 — issue the request unauthenticated to collect the challenge.
    const step1 = await fetchWithTimeout(url, fetchInit, timeout);
    if (step1.status !== 401) {
        return step1;
    }
    const wwwAuth = step1.headers.get('www-authenticate');
    if (!wwwAuth) {
        return step1;
    }
    // Step 2 — compute the digest response and retry.
    const challenge = parseDigestChallenge(wwwAuth);
    const uri = new URL(url).pathname;
    const authorization = buildDigestAuthorization({
        username: username,
        password: password,
        method: method,
        uri: uri,
        challenge: challenge
    });
    return fetchWithTimeout(url, Object.assign(Object.assign({}, fetchInit), { headers: Object.assign(Object.assign({}, fetchInit.headers), { Authorization: authorization }) }), timeout);
}
exports.fetchWithDigest = fetchWithDigest;
function fetchWithTimeout(url, init, timeout) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    return exports.httpClient.fetch(url, Object.assign(Object.assign({}, init), { signal: controller.signal }))
        .finally(() => clearTimeout(timer));
}
//parse the comma-separated key/value pairs out of a `WWW-Authenticate: Digest ...` header. Values may be bare or double-quoted.
function parseDigestChallenge(header) {
    var _a;
    const out = {};
    const body = header.replace(/^Digest\s+/i, '');
    const re = /([a-zA-Z]+)=(?:"((?:[^"\\]|\\.)*)"|([^,]+))/g;
    let m;
    while ((m = re.exec(body)) !== null) {
        out[m[1].toLowerCase()] = (_a = m[2]) !== null && _a !== void 0 ? _a : m[3].trim();
    }
    return out;
}
exports.parseDigestChallenge = parseDigestChallenge;
function md5(input) {
    return crypto.createHash('md5').update(input).digest('hex');
}
//build an RFC 2617 `Authorization: Digest ...` header from a parsed challenge.
function buildDigestAuthorization(params) {
    var _a, _b, _c;
    const { username, password, method, uri, challenge } = params;
    const realm = (_a = challenge.realm) !== null && _a !== void 0 ? _a : '';
    const nonce = (_b = challenge.nonce) !== null && _b !== void 0 ? _b : '';
    const qop = challenge.qop;
    const algorithm = ((_c = challenge.algorithm) !== null && _c !== void 0 ? _c : 'MD5').toUpperCase();
    const cnonce = crypto.randomBytes(8).toString('hex');
    const nc = '00000001';
    const ha1 = algorithm === 'MD5-SESS'
        ? md5(`${md5(`${username}:${realm}:${password}`)}:${nonce}:${cnonce}`)
        : md5(`${username}:${realm}:${password}`);
    const ha2 = md5(`${method}:${uri}`);
    const response = qop
        ? md5(`${ha1}:${nonce}:${nc}:${cnonce}:${qop}:${ha2}`)
        : md5(`${ha1}:${nonce}:${ha2}`);
    const parts = [
        `username="${username}"`,
        `realm="${realm}"`,
        `nonce="${nonce}"`,
        `uri="${uri}"`,
        `algorithm=${algorithm}`,
        `response="${response}"`
    ];
    if (qop) {
        parts.push(`qop=${qop}`, `nc=${nc}`, `cnonce="${cnonce}"`);
    }
    if (challenge.opaque) {
        parts.push(`opaque="${challenge.opaque}"`);
    }
    return `Digest ${parts.join(', ')}`;
}
exports.buildDigestAuthorization = buildDigestAuthorization;
//# sourceMappingURL=fetch.js.map