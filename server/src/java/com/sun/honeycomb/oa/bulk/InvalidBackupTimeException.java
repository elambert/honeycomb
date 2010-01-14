package com.sun.honeycomb.oa.bulk;

public class InvalidBackupTimeException extends IllegalArgumentException {

    public InvalidBackupTimeException() {
    }

    public InvalidBackupTimeException(String msg) {
        super(msg);
    }

    public InvalidBackupTimeException(Throwable cause) {
        super(cause);
    }

    public InvalidBackupTimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
