class ApplicationError(Exception):
    """Business failure translated at the HTTP boundary."""
    def __init__(self, status: int, detail, headers=None):
        super().__init__(str(detail))
        self.status = status
        self.detail = detail
        self.headers = headers
