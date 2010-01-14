package com.sun.dtf.cluster.snapshot;

import java.io.File;
import java.io.IOException;

public class DeleteTask extends Task {

    public DeleteTask(File source, File destination, TaskGenerator generator) {
        super(source, destination, generator);
    }

    protected void operate(File source, File destination) throws IOException {
        source.delete();
    }
}
