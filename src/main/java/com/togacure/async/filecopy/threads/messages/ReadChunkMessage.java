package com.togacure.async.filecopy.threads.messages;

import com.togacure.async.filecopy.mem.Chunk;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReadChunkMessage extends SingleOperationMessage {

	@NonNull
	private final Chunk chunk;
}
