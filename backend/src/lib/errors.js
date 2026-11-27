export class AppError extends Error {
  constructor(status, code, message, headers = null) {
    super(message);
    this.status = status;
    this.code = code;
    this.headers = headers;
  }
}
