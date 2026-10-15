"""HTTP Client with retries and browser headers for TUSUR schedule."""

import logging
from typing import Optional

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from src.config import DEFAULT_HEADERS

logger = logging.getLogger("tusur_client")


def create_http_session() -> requests.Session:
    """Creates a requests session with retry strategy and default headers."""
    session = requests.Session()
    session.headers.update(DEFAULT_HEADERS)
    retries = Retry(
        total=5,
        backoff_factor=1,
        status_forcelist=[429, 500, 502, 503, 504],
        raise_on_status=False,
    )
    adapter = HTTPAdapter(max_retries=retries)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


def fetch_page(url: str, session: Optional[requests.Session] = None) -> str:
    """Fetches HTML page from the given URL with error handling and timeouts."""
    close_session = False
    if session is None:
        session = create_http_session()
        close_session = True

    try:
        logger.info("Fetching schedule from %s", url)
        response = session.get(url, timeout=20)
        response.raise_for_status()
        response.encoding = response.apparent_encoding or "utf-8"
        return response.text
    except requests.RequestException as e:
        logger.error("Failed to fetch schedule from %s: %s", url, e)
        raise
    finally:
        if close_session:
            session.close()
