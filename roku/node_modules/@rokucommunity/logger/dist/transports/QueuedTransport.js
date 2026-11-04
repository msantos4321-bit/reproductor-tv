"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.QueuedTransport = void 0;
/**
 * A {@link Transport} that holds incoming messages in an in-memory queue until a writer function is supplied.
 * Once a writer is set, queued messages are flushed to it and subsequent messages are written immediately.
 * Useful as a base for transports whose destination isn't available yet (see {@link FileTransport}).
 * @public
 */
class QueuedTransport {
    constructor(
    /**
     * A function to be called any time a message needs to be written
     */
    writer) {
        this.messageQueue = [];
        this.setWriter(writer);
    }
    setWriter(writer) {
        this.writer = writer;
        if (typeof this.writer === 'function') {
            try {
                if (this.messageQueue.length > 0) {
                    for (const message of this.messageQueue) {
                        this.writer(message);
                    }
                }
            }
            finally {
                this.messageQueue = [];
            }
        }
    }
    pipe(message) {
        if (this.writer) {
            this.writer(message);
        }
        else {
            this.messageQueue.push(message);
        }
    }
}
exports.QueuedTransport = QueuedTransport;
//# sourceMappingURL=QueuedTransport.js.map