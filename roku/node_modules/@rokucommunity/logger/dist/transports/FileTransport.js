"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FileTransport = void 0;
const fs = require("fs");
const QueuedTransport_1 = require("./QueuedTransport");
const path = require("path");
/**
 * A {@link Transport} that appends formatted log messages to a file. Messages logged before a file path
 * is set are queued and flushed once {@link FileTransport.setLogFilePath} is called with a valid path.
 * @public
 */
class FileTransport extends QueuedTransport_1.QueuedTransport {
    constructor(logFilePath) {
        super();
        this.setLogFilePath(logFilePath);
    }
    setLogFilePath(logfilePath) {
        //if we have a logfile path, set the writer function which will flush the logs and enable future logging
        if (typeof logfilePath === 'string') {
            this.setWriter((message) => {
                //make sure the parent directory exists
                fs.mkdirSync(path.dirname(logfilePath), { recursive: true });
                //append the log entry to the file
                fs.appendFileSync(logfilePath, message.logger.formatMessage(message) + '\n');
            });
        }
        else {
            this.setWriter(undefined);
        }
    }
}
exports.FileTransport = FileTransport;
//# sourceMappingURL=FileTransport.js.map