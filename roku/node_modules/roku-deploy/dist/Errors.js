"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isConnectionResetError = exports.ConnectionResetError = exports.isUpdateCheckRequiredError = exports.UpdateCheckRequiredError = exports.UnsupportedFirmwareVersionError = exports.MissingRequiredOptionError = exports.ConvertError = exports.CompileError = exports.DeviceUnreachableError = exports.UnknownDeviceResponseError = exports.FailedDeviceResponseError = exports.UnparsableDeviceResponseError = exports.EcpNetworkAccessModeDisabledError = exports.UnauthorizedDeviceResponseError = exports.InvalidDeviceResponseCodeError = void 0;
class InvalidDeviceResponseCodeError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, InvalidDeviceResponseCodeError.prototype);
    }
}
exports.InvalidDeviceResponseCodeError = InvalidDeviceResponseCodeError;
class UnauthorizedDeviceResponseError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, UnauthorizedDeviceResponseError.prototype);
    }
}
exports.UnauthorizedDeviceResponseError = UnauthorizedDeviceResponseError;
class EcpNetworkAccessModeDisabledError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, EcpNetworkAccessModeDisabledError.prototype);
    }
}
exports.EcpNetworkAccessModeDisabledError = EcpNetworkAccessModeDisabledError;
class UnparsableDeviceResponseError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, UnparsableDeviceResponseError.prototype);
    }
}
exports.UnparsableDeviceResponseError = UnparsableDeviceResponseError;
class FailedDeviceResponseError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, FailedDeviceResponseError.prototype);
    }
}
exports.FailedDeviceResponseError = FailedDeviceResponseError;
class UnknownDeviceResponseError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, UnknownDeviceResponseError.prototype);
    }
}
exports.UnknownDeviceResponseError = UnknownDeviceResponseError;
class DeviceUnreachableError extends Error {
    constructor(message, results) {
        super(message);
        this.results = results;
        Object.setPrototypeOf(this, DeviceUnreachableError.prototype);
    }
}
exports.DeviceUnreachableError = DeviceUnreachableError;
class CompileError extends Error {
    constructor(message, results, rokuMessages) {
        super(message);
        this.results = results;
        this.rokuMessages = rokuMessages;
        Object.setPrototypeOf(this, CompileError.prototype);
    }
}
exports.CompileError = CompileError;
class ConvertError extends Error {
    constructor(message) {
        super(message);
        Object.setPrototypeOf(this, ConvertError.prototype);
    }
}
exports.ConvertError = ConvertError;
class MissingRequiredOptionError extends Error {
    constructor(message) {
        super(message);
        Object.setPrototypeOf(this, MissingRequiredOptionError.prototype);
    }
}
exports.MissingRequiredOptionError = MissingRequiredOptionError;
class UnsupportedFirmwareVersionError extends Error {
    constructor(message) {
        super(message);
        Object.setPrototypeOf(this, UnsupportedFirmwareVersionError.prototype);
    }
}
exports.UnsupportedFirmwareVersionError = UnsupportedFirmwareVersionError;
/**
 * This error is thrown when a Roku device refuses to accept connections because it requires the user to check for updates (even if no updates are actually available).
 */
class UpdateCheckRequiredError extends Error {
    constructor(response, requestOptions, cause) {
        super();
        this.response = response;
        this.requestOptions = requestOptions;
        this.cause = cause;
        this.message = UpdateCheckRequiredError.MESSAGE;
        Object.setPrototypeOf(this, UpdateCheckRequiredError.prototype);
    }
}
exports.UpdateCheckRequiredError = UpdateCheckRequiredError;
UpdateCheckRequiredError.MESSAGE = `Your device needs to check for updates before accepting connections. Please navigate to System Settings and check for updates and then try again.\n\nhttps://support.roku.com/article/208755668.`;
function isUpdateCheckRequiredError(e) {
    var _a;
    return ((_a = e === null || e === void 0 ? void 0 : e.constructor) === null || _a === void 0 ? void 0 : _a.name) === 'UpdateCheckRequiredError';
}
exports.isUpdateCheckRequiredError = isUpdateCheckRequiredError;
/**
 * This error is thrown when a Roku device ends the connection unexpectedly, causing an 'ECONNRESET' error. Typically this happens when the device needs to check for updates (even if no updates are available), but it can also happen for other reasons.
 */
class ConnectionResetError extends Error {
    constructor(error, requestOptions) {
        super();
        this.message = ConnectionResetError.MESSAGE;
        this.cause = error;
        Object.setPrototypeOf(this, ConnectionResetError.prototype);
    }
}
exports.ConnectionResetError = ConnectionResetError;
ConnectionResetError.MESSAGE = `The Roku device ended the connection unexpectedly and may need to check for updates before accepting connections. Please navigate to System Settings and check for updates and then try again.\n\nhttps://support.roku.com/article/208755668.`;
function isConnectionResetError(e) {
    var _a;
    return ((_a = e === null || e === void 0 ? void 0 : e.constructor) === null || _a === void 0 ? void 0 : _a.name) === 'ConnectionResetError';
}
exports.isConnectionResetError = isConnectionResetError;
//# sourceMappingURL=Errors.js.map