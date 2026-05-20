# services/gemini_client.py
# Gemini client with automatic API key rotation on quota exhaustion (429).
# Add fallback keys to .env as GOOGLE_API_KEY_2, GOOGLE_API_KEY_3, etc.

import logging
import os
import re
import time

logger = logging.getLogger(__name__)


def _load_keys() -> list[str]:
    keys = []
    primary = os.getenv("GOOGLE_API_KEY", "")
    if primary:
        keys.append(primary)
    for i in range(2, 10):
        k = os.getenv(f"GOOGLE_API_KEY_{i}", "")
        if k:
            keys.append(k)
    return keys


def generate_with_fallback(model: str, contents: str) -> object:
    """
    Call Gemini generate_content, rotating through all configured API keys on 429.
    Retries the same key on 503 with exponential backoff.
    Raises the last exception if all keys are exhausted.
    """
    from google import genai

    keys = _load_keys()
    if not keys:
        raise RuntimeError("No GOOGLE_API_KEY configured")

    last_exc = None
    for key_idx, api_key in enumerate(keys):
        client = genai.Client(api_key=api_key)
        key_label = f"key[{key_idx + 1}/{len(keys)}]"

        for attempt in range(4):
            try:
                response = client.models.generate_content(model=model, contents=contents)
                if key_idx > 0:
                    logger.info(f"Gemini succeeded on {key_label}")
                return response
            except Exception as e:
                last_exc = e
                err = str(e)

                if "429" in err or "RESOURCE_EXHAUSTED" in err:
                    logger.warning(f"Gemini {key_label} quota exhausted — trying next key")
                    break  # Move to next key immediately

                if attempt < 3:
                    hint = re.search(r"retryDelay.*?(\d+)s", err)
                    wait = int(hint.group(1)) + 2 if hint else 2 ** (attempt + 1)
                    logger.warning(f"Gemini {key_label} transient error, retrying in {wait}s")
                    time.sleep(wait)
                else:
                    break  # Move to next key after 4 failed attempts

    raise last_exc
