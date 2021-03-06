package com.togacure.async.filecopy.threads;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.togacure.async.filecopy.mem.MemoryBuffer;
import com.togacure.async.filecopy.threads.messages.FreeChunkMessage;
import com.togacure.async.filecopy.threads.messages.ReadChunkMessage;
import com.togacure.async.filecopy.threads.messages.SingleOperationMessage;
import com.togacure.async.filecopy.ui.IUICopyCompleteObserver;
import com.togacure.async.filecopy.ui.IUIThreadStateObserver;
import com.togacure.async.filecopy.util.exceptions.CloseFileException;
import com.togacure.async.filecopy.util.exceptions.FileOperationException;
import com.togacure.async.filecopy.util.exceptions.OperationDeniedException;
import com.togacure.async.filecopy.util.exceptions.ThreadStopException;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class WriteFileThread extends AbstractThread {

	@NonNull
	private final MemoryBuffer buffer;

	@NonNull
	private final IMessageReceiver readThreadReceiver;

	@Getter
	@NonNull
	private final IUIThreadStateObserver threadStateObserver;

	@Getter
	@NonNull
	private final IUICopyCompleteObserver copyCompleteObserver;

	// TODO single write thread
	private final ThreadLocal<OutputStream> outputStream = new ThreadLocal<OutputStream>();

	@Override
	public void handleMessage(SingleOperationMessage message) throws ThreadStopException {
		log.debug("message: {}", message);
		super.handleMessage(message);
		if (message instanceof ReadChunkMessage) {
			buffer.out(outputStream.get(), ((ReadChunkMessage) message).getChunk());
			readThreadReceiver.receiveMessage(new FreeChunkMessage());
		}
	}

	@Override
	public void openFile() throws OperationDeniedException {
		try {
			if (outputStream.get() != null) {
				closeFile();
			}
			outputStream.set(new FileOutputStream(getFileDescriptor().toPath()));
		} catch (FileNotFoundException e) {
			throw new FileOperationException(e.getMessage());
		}
	}

	@Override
	public void closeFile() throws CloseFileException {
		val os = outputStream.get();
		outputStream.set(null);
		try {
			os.close();
		} catch (IOException e) {
			throw new CloseFileException(e.getMessage());
		}
	}

}
