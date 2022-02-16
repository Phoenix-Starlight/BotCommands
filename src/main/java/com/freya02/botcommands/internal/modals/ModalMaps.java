package com.freya02.botcommands.internal.modals;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ModalMaps {
	private static final long MAX_ID = Long.MAX_VALUE;
	private static final long MIN_ID = (long) Math.pow(10, Math.floor(Math.log10(MAX_ID))); //Same amount of digits except every digit is 0 but the first one is 1

	private final Map<String, ModalData> modalMap = new HashMap<>();

	//Modals input IDs are temporarily stored here while it waits for its ModalBuilder owner to be built, and it's InputData to be associated with it
	private final Map<String, InputData> inputMap = new HashMap<>();

	private String insertModal(ModalData data) {
		final ThreadLocalRandom random = ThreadLocalRandom.current();

		synchronized (modalMap) {
			String id;

			do {
				id = String.valueOf(random.nextLong(MIN_ID, MAX_ID));
			} while (modalMap.containsKey(id));

			modalMap.put(id, data);

			return id;
		}
	}

	public String insertModal(ModalData data, String id) {
		if (id == null || id.equals("0")) {
			return insertModal(data);
		} else {
			synchronized (modalMap) {
				modalMap.put(id, data);

				return id;
			}
		}
	}

	@Nullable
	public ModalData getModalData(String modalId) {
		return modalMap.get(modalId);
	}

	private String insertInput(InputData data) {
		final ThreadLocalRandom random = ThreadLocalRandom.current();

		synchronized (inputMap) {
			String id;

			do {
				id = String.valueOf(random.nextLong(MIN_ID, MAX_ID));
			} while (inputMap.containsKey(id));

			inputMap.put(id, data);

			return id;
		}
	}

	public String insertInput(InputData data, String id) {
		if (id == null || id.equals("0")) {
			return insertInput(data);
		} else {
			synchronized (inputMap) {
				inputMap.put(id, data);

				return id;
			}
		}
	}

	@Nullable
	public InputData removeInput(String inputId) {
		synchronized (inputMap) {
			return inputMap.remove(inputId);
		}
	}
}
