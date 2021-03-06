package com.togacure.async.filecopy.util;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import com.togacure.async.filecopy.util.exceptions.OperationDeniedException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Syncronized {

	@SneakyThrows({ InterruptedException.class, OperationDeniedException.class })
	public static final void execute(@NonNull Lock lock, @NonNull IOperationCallback callback) {
		lock.lockInterruptibly();
		try {
			callback.call();
		} finally {
			lock.unlock();
		}
	}

	public static final void throwsExecute(@NonNull Lock lock, @NonNull IOperationCallback callback)
			throws OperationDeniedException {
		execute(lock, callback);
	}

	@SneakyThrows(InterruptedException.class)
	public static final <T> T get(@NonNull Lock lock, @NonNull Supplier<T> supplier) {
		lock.lockInterruptibly();
		try {
			return supplier.get();
		} finally {
			lock.unlock();
		}
	}

	public static final <T> T throwsGet(@NonNull Lock lock, @NonNull Supplier<T> supplier)
			throws OperationDeniedException {
		return get(lock, supplier);
	}
}
