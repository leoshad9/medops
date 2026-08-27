export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
}

export interface ErrorDetail {
  code: number;
  status: string;
  message: string;
  details: string[] | null;
}

export interface ErrorResponse {
  success: false;
  error: ErrorDetail;
}
