public enum Disease {
	CHOLERA("c"),
	DENGUE("d"),
	MALARIA("m"),
	JP_ENCEPH("j"),
	TYPHOID("t"),
	HEPATITIS("h"),
	COVID19("v");

	private final String code;

	Disease(String code) {
		this.code = code;
	}

	static Disease lookupViaCode(String code) {
		if (code != null || !code.isBlank())
			for (Disease disease : Disease.values()) {
				if (disease.getCode().equalsIgnoreCase(code)) {
					return disease;
				}
			}
		return null;
	}

	public String getCode() {
		return code;
	}
}