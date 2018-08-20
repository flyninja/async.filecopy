package com.togacure.async.filecopy.threads;

import static com.togacure.async.filecopy.util.Syncronized.get;
import static com.togacure.async.filecopy.util.Syncronized.throwsExecute;
import static com.togacure.async.filecopy.util.Syncronized.throwsGet;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.togacure.async.filecopy.threads.messages.ChangeStateMessage;
import com.togacure.async.filecopy.threads.messages.EOFMessage;
import com.togacure.async.filecopy.threads.messages.IMessage;
import com.togacure.async.filecopy.threads.messages.ResumeOperationsMessage;
import com.togacure.async.filecopy.threads.messages.SingleOperationMessage;
import com.togacure.async.filecopy.threads.messages.SuspendOperationsMessage;
import com.togacure.async.filecopy.util.FileDescriptor;
import com.togacure.async.filecopy.util.Utils;
import com.togacure.async.filecopy.util.exceptions.FileNotSelectedException;
import com.togacure.async.filecopy.util.exceptions.IncorrectFlowState;
import com.togacure.async.filecopy.util.exceptions.OperationDeniedException;
import com.togacure.async.filecopy.util.exceptions.ThreadStopException;

import lombok.SneakyThrows;

public abstract class AbstractThread implements IThread, IMessageReceiver {

	private static final int DEFAULT_INITIAL_CAPACITY = 32;

	private static final int INITIAL_THREAD_POOL_SIZE = 2;

	private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(INITIAL_THREAD_POOL_SIZE);

	private FileDescriptor fileDescriptor;

	private ThreadState currentState = ThreadState.death;

	private final Lock lock = new ReentrantLock(true);

	private final Object sleepMonitor = new Object();

	private final BlockingQueue<IMessage> messageQueue = new PriorityBlockingQueue<IMessage>(DEFAULT_INITIAL_CAPACITY,
			(v1, v2) -> {
				return v1.compareTo(v2);
			});

	@Override
	public FileDescriptor getFileDescriptor() {
		return get(lock, () -> fileDescriptor);
	}

	@Override
	public ThreadState getCurrentState() {
		return get(lock, () -> currentState);
	}

	@Override
	@SneakyThrows({ InterruptedException.class, OperationDeniedException.class })
	public void run() {
		IMessage message;
		while ((message = messageQueue.take()) != null) {
			if (message instanceof ChangeStateMessage) {
				switch (getCurrentState()) {
				case alive:
					try {
						handleMessage(new ResumeOperationsMessage());
					} catch (ThreadStopException e) {
						setState(ThreadState.death);
					}
					break;
				case paused:
					try {
						handleMessage(new SuspendOperationsMessage());
					} catch (ThreadStopException e) {
						setState(ThreadState.death);
					}
					break;
				case death:
					closeFile();
					return;
				}
			} else if (message instanceof SingleOperationMessage) {
				try {
					handleMessage((SingleOperationMessage) message);
				} catch (ThreadStopException e) {
					setState(ThreadState.death);
				}
			} else {
				throw new RuntimeException(String.format("Unknown message %s", message));
			}
		}
	}

	@Override
	@SneakyThrows(InterruptedException.class)
	public void handleMessage(SingleOperationMessage message) throws ThreadStopException {
		if (message instanceof ResumeOperationsMessage) {
			getLabelObserver().setLabelValue(currentState.name());
		} else if (message instanceof SuspendOperationsMessage) {
			getLabelObserver().setLabelValue(currentState.name());
			synchronized (sleepMonitor) {
				sleepMonitor.wait();
			}
		} else if (message instanceof EOFMessage) {
			throw new ThreadStopException();
		}
	}

	@Override
	public void switchFile(FileDescriptor fd) throws OperationDeniedException {
		throwsExecute(lock, () -> {
			switch (currentState) {
			case alive:
			case paused:
				throw new OperationDeniedException(
						"Can not perform an operation on the thread that performs the task.");
			case death:
				checkFileDescriptor(fd);
				fileDescriptor = fd;
			}
		});
	}

	@Override
	public ThreadState switchState() throws OperationDeniedException {
		return throwsGet(lock, () -> {
			switch (currentState) {
			case alive:
				currentState = ThreadState.paused;
				break;
			case paused:
				checkFileDescriptor(fileDescriptor);
				currentState = ThreadState.alive;
				synchronized (sleepMonitor) {
					sleepMonitor.notify();
				}
				break;
			case death:
				checkFileDescriptor(fileDescriptor);
				currentState = ThreadState.alive;
				startTask();
			}
			putMessageWrapper(new ChangeStateMessage());
			return currentState;
		});
	}

	@Override
	public void receiveMessage(IMessage message) {
		putMessageWrapper(message);
	}

	protected void setState(ThreadState state) throws OperationDeniedException {
		throwsExecute(lock, () -> {
			switch (currentState) {
			case alive:
				currentState = Optional.ofNullable(state).filter((v) -> {
					return v == ThreadState.paused || v == ThreadState.death;
				}).orElseThrow(IncorrectFlowState::new);
				break;
			case paused:
				checkFileDescriptor(fileDescriptor);
				currentState = Optional.ofNullable(state).filter((v) -> {
					return v == ThreadState.alive || v == ThreadState.death;
				}).orElseThrow(IncorrectFlowState::new);
				if (state == ThreadState.alive) {
					synchronized (sleepMonitor) {
						sleepMonitor.notify();
					}
				}
				break;
			case death:
				checkFileDescriptor(fileDescriptor);
				openFile();
				currentState = Optional.ofNullable(state).filter((v) -> {
					return v == ThreadState.alive;
				}).orElseThrow(IncorrectFlowState::new);
				startTask();
			}
			putMessageWrapper(new ChangeStateMessage());
		});
	}

	private void startTask() {
		THREAD_POOL.submit(this);
	}

	@SneakyThrows(InterruptedException.class)
	protected void putMessageWrapper(IMessage msg) {
		messageQueue.put(msg);
	}

	@SneakyThrows(FileNotSelectedException.class)
	private static void checkFileDescriptor(FileDescriptor fd) {
		Optional.ofNullable(fd).map(v -> v.toPath()).filter(Utils::isNotNullOrEmpty)
				.orElseThrow(FileNotSelectedException::new);
	}

}
