export interface Order {
  id: string;
  storeName: string;
  receptionistId?: string;
  receptionistName?: string;
  businessDate: string;
  arrivalScheduledStartTime?: string;
  arrivalScheduledEndTime?: string;
  customerId?: string;
  customerName?: string;
  girlId?: string;
  girlName?: string;
  courseMinutes: number;
  extensionMinutes: number;
  optionCodes: string[];
  discountName?: string;
  manualDiscount: number;
  carrier?: string;
  mediaName?: string;
  usedPoints: number;
  manualGrantPoints: number;
  remarks?: string;
  girlDriverMessage?: string;
  status: string;
}

export interface OrderCreateRequest {
  storeName: string;
  receptionistId?: string;
  businessDate: string;
  arrivalScheduledStartTime?: string;
  arrivalScheduledEndTime?: string;
  customerId?: string;
  customerName?: string;
  girlId?: string;
  courseMinutes: number;
  extensionMinutes: number;
  optionCodes: string[];
  discountName?: string;
  manualDiscount: number;
  carrier?: string;
  mediaName?: string;
  usedPoints: number;
  manualGrantPoints: number;
  remarks?: string;
  girlDriverMessage?: string;
  // Customer Creation Fields
  phoneNumber?: string;
  phoneNumber2?: string;
  address?: string;
  buildingName?: string;
  classification?: string;
  landmark?: string;
  hasPet?: boolean;
  ngType?: string;
  ngContent?: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}
