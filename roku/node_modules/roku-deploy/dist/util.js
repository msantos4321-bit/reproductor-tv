"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.standardizePath = exports.defer = exports.util = exports.Util = void 0;
const fsExtra = require("fs-extra");
const path = require("path");
const fs = require("fs");
const os = require("os");
const dns = require("dns");
const crypto = require("crypto");
const micromatch = require("micromatch");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const fastGlob = require("fast-glob");
class Util {
    constructor() {
        //Map<filesystem root path, is case-sensitive>
        this.isFileSystemCaseSensitiveCache = new Map();
        this.dnsCache = new Map();
    }
    /**
     * Determine if `childPath` is contained within the `parentPath`
     * @param parentPath
     * @param childPath
     * @param standardizePaths if false, the paths are assumed to already be in the same format and are not re-standardized
     */
    isParentOfPath(parentPath, childPath, standardizePaths = true) {
        if (standardizePaths) {
            parentPath = exports.util.standardizePath(parentPath);
            childPath = exports.util.standardizePath(childPath);
        }
        const relative = path.relative(parentPath, childPath);
        return relative && !relative.startsWith('..') && !path.isAbsolute(relative);
    }
    /**
     * Determines if the given path is a file
     * @param filePathAbsolute
     */
    async isFile(filePathAbsolute) {
        try {
            //get the full path to the file. This should be the same path for files, and the actual path for any symlinks
            let realPathAbsolute = fs.realpathSync(filePathAbsolute);
            let stat = await fsExtra.lstat(realPathAbsolute);
            return stat.isFile();
        }
        catch (e) {
            // lstatSync throws an error if path doesn't exist
            return false;
        }
    }
    /**
     * Normalize path and replace all directory separators with current OS separators
     * @param thePath
     */
    standardizePath(thePath) {
        if (!thePath) {
            return thePath;
        }
        return path.normalize(thePath.replace(/[\/\\]+/g, path.sep));
    }
    /**
     * Convert all slashes to forward slashes
     */
    toForwardSlashes(thePath) {
        if (typeof thePath === 'string') {
            return thePath.replace(/[\/\\]+/g, '/');
        }
        else {
            return thePath;
        }
    }
    /**
     * Do a case-insensitive string replacement
     * @param subject the string that will have its contents replaced
     * @param search the search text to find in `subject`
     * @param replace the text to replace `search` with in `subject`
     */
    stringReplaceInsensitive(subject, search, replace) {
        let idx = subject.toLowerCase().indexOf(search.toLowerCase());
        if (idx > -1) {
            return subject.substring(0, idx) + replace + subject.substr(idx + search.length);
        }
        else {
            return subject;
        }
    }
    /**
     * Keep calling the callback until it does NOT throw an exception, or until the max number of tries has been reached.
     * @param callback
     * @param maxTries
     * @param sleepMilliseconds
     */
    /* istanbul ignore next */ //typescript generates some weird while statement that can't get fully covered for some reason
    async tryRepeatAsync(callback, maxTries = 10, sleepMilliseconds = 50) {
        let tryCount = 0;
        while (true) {
            try {
                return await Promise.resolve(callback());
            }
            catch (e) {
                tryCount++;
                if (tryCount > maxTries) {
                    throw e;
                }
                else {
                    await this.sleep(sleepMilliseconds);
                }
            }
        }
    }
    async sleep(milliseconds) {
        await new Promise((resolve) => {
            setTimeout(resolve, milliseconds);
        });
    }
    /**
     * Determine if a file exists (case insensitive)
     */
    async fileExistsCaseInsensitive(filePath) {
        filePath = this.standardizePath(filePath);
        const lowerFilePath = filePath.toLowerCase();
        const parentDirPath = path.dirname(filePath);
        //file can't exist if its parent dir doesn't exist
        if (await fsExtra.pathExists(parentDirPath) === false) {
            return false;
        }
        //get a list of every file in the parent directory for this file
        const filesInDir = await fsExtra.readdir(parentDirPath);
        //look at each file path until we find the one we're searching for
        for (let dirFile of filesInDir) {
            const dirFilePath = this.standardizePath(`${parentDirPath}/${dirFile}`);
            if (dirFilePath.toLowerCase() === lowerFilePath) {
                return true;
            }
        }
        return false;
    }
    /**
     * Run a series of glob patterns, returning the matches in buckets corresponding to their pattern index.
     */
    async globAllByIndex(patterns, cwd) {
        //force all path separators to unix style
        cwd = cwd.replace(/\\/g, '/');
        const isFileSystemCaseSensitive = await this.getIsFileSystemCaseSensitive(cwd);
        const globResults = patterns.map(async (pattern) => {
            //force all windows-style slashes to unix style
            pattern = pattern.replace(/\\/g, '/');
            //skip negated patterns (we will use them to filter later on)
            if (pattern.startsWith('!')) {
                return pattern;
            }
            else {
                //run glob matcher
                return fastGlob([pattern], {
                    cwd: cwd,
                    absolute: true,
                    followSymbolicLinks: true,
                    onlyFiles: true,
                    caseSensitiveMatch: isFileSystemCaseSensitive
                });
            }
        });
        const matchesByIndex = [];
        for (let i = 0; i < globResults.length; i++) {
            const globResult = await globResults[i];
            //if the matches collection is missing, this is a filter
            if (typeof globResult === 'string') {
                this.filterPaths(globResult, matchesByIndex, cwd, i - 1);
                matchesByIndex.push(undefined);
            }
            else {
                matchesByIndex.push(globResult);
            }
        }
        return matchesByIndex;
    }
    async getIsFileSystemCaseSensitive(cwd) {
        cwd = this.standardizePath(cwd);
        const root = this.standardizePath(path.parse(cwd).root);
        const cachedValue = this.isFileSystemCaseSensitiveCache.get(root);
        if (cachedValue !== undefined) {
            return cachedValue;
        }
        const probeFileRandomBytes = 8;
        const testFileBase = `roku_deploy_case_check_${crypto.randomBytes(probeFileRandomBytes).toString('hex')}.txt`;
        const upperCasePath = path.resolve(cwd, testFileBase.toUpperCase());
        const lowerCasePath = path.resolve(cwd, testFileBase.toLowerCase());
        try {
            await fsExtra.ensureDir(cwd);
            await fsExtra.outputFile(upperCasePath, 'case-check');
            const isCaseSensitive = !(await fsExtra.pathExists(lowerCasePath));
            this.isFileSystemCaseSensitiveCache.set(root, isCaseSensitive);
            return isCaseSensitive;
        }
        catch (_a) {
            //if we cannot probe the filesystem (permissions/read-only/etc), default to case-sensitive matching
            //to avoid unintentionally broadening glob matches.
            this.isFileSystemCaseSensitiveCache.set(root, true);
            return true;
        }
        finally {
            await fsExtra.remove(upperCasePath);
        }
    }
    /**
     * Filter all of the matches based on a minimatch pattern
     * @param stopIndex the max index of `matchesByIndex` to filter until
     * @param pattern - the pattern used to filter out entries from `matchesByIndex`. Usually preceeded by a `!`
     */
    filterPaths(pattern, filesByIndex, cwd, stopIndex) {
        //move the ! to the start of the string to negate the absolute path, replace windows slashes with unix ones
        let negatedPatternAbsolute = '!' + path.resolve(cwd, pattern.replace(/^!/, '')).replace(/\\/g, '/');
        let filter = micromatch.matcher(negatedPatternAbsolute);
        for (let i = 0; i <= stopIndex; i++) {
            if (filesByIndex[i]) {
                //filter all matches by the specified pattern
                filesByIndex[i] = filesByIndex[i].filter(x => {
                    return filter(x);
                });
            }
        }
    }
    /*
     * Look up the ip address for a hostname. This is cached for the lifetime of the app, or bypassed with the `skipCache` parameter
     * @param host
     * @param skipCache
     * @returns
     */
    async dnsLookup(host, skipCache = false) {
        var _a;
        if (!this.dnsCache.has(host) || skipCache) {
            const result = await dns.promises.lookup(host);
            this.dnsCache.set(host, (_a = result.address) !== null && _a !== void 0 ? _a : host);
        }
        return this.dnsCache.get(host);
    }
    /**
     * Decode HTML entities like &nbsp; &#39; to its original character
     */
    decodeHtmlEntities(encodedString) {
        let translateRegex = /&(nbsp|amp|quot|lt|gt);/g;
        let translate = {
            'nbsp': ' ',
            'amp': '&',
            'quot': '"',
            'lt': '<',
            'gt': '>'
        };
        return encodedString.replace(translateRegex, (match, entity) => {
            return translate[entity];
        }).replace(/&#(\d+);/gi, (match, numStr) => {
            let num = parseInt(numStr, 10);
            return String.fromCharCode(num);
        });
    }
    /**
     * The OS temp directory, with symlinks resolved (e.g. on macOS `/var` -> `/private/var`).
     * Replaces the `temp-dir` package. Cached on first access.
     */
    get tempDir() {
        if (this._tempDir === undefined) {
            this._tempDir = fs.realpathSync(os.tmpdir());
        }
        return this._tempDir;
    }
    /**
     * Convert a string to camelCase (e.g. `device-id` -> `deviceId`, `has-wifi-5g-support` -> `hasWifi5GSupport`).
     * Replaces `lodash.camelCase`. Reproduces lodash's word-splitting (boundaries at non-alphanumeric
     * runs, lower->UPPER transitions, acronym->Word transitions, and digit<->letter transitions),
     * verified against lodash across the full Roku device-info key set.
     */
    camelCase(value) {
        let matches = String(value !== null && value !== void 0 ? value : '').matchAll(Util.wordSplitRegex);
        let result = '';
        let index = 0;
        for (let match of matches) {
            let word = match[0].toLowerCase();
            if (index === 0) {
                result += word;
            }
            else {
                result += word.charAt(0).toUpperCase() + word.slice(1);
            }
            index++;
        }
        return result;
    }
}
exports.Util = Util;
Util.wordSplitRegex = /[A-Z\xc0-\xd6\xd8-\xde]?[a-z\xdf-\xf6\xf8-\xff]+(?:['’](?:d|ll|m|re|s|t|ve))?(?=[A-Z\xc0-\xd6\xd8-\xde]|\b|_|\d)|[A-Z\xc0-\xd6\xd8-\xde]+(?:['’](?:D|LL|M|RE|S|T|VE))?(?=[A-Z\xc0-\xd6\xd8-\xde][a-z\xdf-\xf6\xf8-\xff]|\b|_|\d)|[A-Z\xc0-\xd6\xd8-\xde]?[a-z\xdf-\xf6\xf8-\xff]+|[A-Z\xc0-\xd6\xd8-\xde]+|\d+/g;
exports.util = new Util();
function defer() {
    let _resolve;
    let _reject;
    let promise = new Promise((resolveValue, rejectValue) => {
        _resolve = resolveValue;
        _reject = rejectValue;
    });
    return {
        promise: promise,
        tryResolve: function tryResolve(value) {
            if (!this.isCompleted) {
                this.resolve(value);
            }
        },
        resolve: function resolve(value) {
            if (!this.isResolved) {
                this.isResolved = true;
                _resolve(value);
                _resolve = undefined;
            }
            else {
                throw new Error(`Attempted to resolve a promise that was already resolved.` +
                    `New value: ${JSON.stringify(value)}`);
            }
        },
        tryReject: function tryReject(reason) {
            if (!this.isCompleted) {
                this.reject(reason);
            }
        },
        reject: function reject(reason) {
            if (!this.isCompleted) {
                this.isRejected = true;
                _reject(reason);
                _reject = undefined;
            }
            else {
                throw new Error(`Attempted to reject a promise that was already ${this.isResolved ? 'resolved' : 'rejected'}.` +
                    `New error message: ${String(reason)}`);
            }
        },
        isResolved: false,
        isRejected: false,
        get isCompleted() {
            return this.isResolved || this.isRejected;
        }
    };
}
exports.defer = defer;
/**
 * A tagged template literal function for standardizing the path.
 */
function standardizePath(stringParts, ...expressions) {
    let result = [];
    for (let i = 0; i < stringParts.length; i++) {
        result.push(stringParts[i], expressions[i]);
    }
    return exports.util.standardizePath(result.join(''));
}
exports.standardizePath = standardizePath;
//# sourceMappingURL=util.js.map