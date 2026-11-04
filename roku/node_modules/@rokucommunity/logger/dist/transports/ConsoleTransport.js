"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConsoleTransport = void 0;
/**
 * A {@link Transport} that writes log messages to the console, routing each message to the matching
 * `console` method (e.g. `console.warn` for warnings) and falling back to `console.log`.
 * @public
 */
class ConsoleTransport {
    pipe(message) {
        const methodName = console[message.logLevel] ? message.logLevel : 'log';
        console[methodName](message.logger.formatLeadingMessageParts(message), ...message.args);
    }
}
exports.ConsoleTransport = ConsoleTransport;
//# sourceMappingURL=ConsoleTransport.js.map