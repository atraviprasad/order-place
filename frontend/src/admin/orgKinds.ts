import type { OrgPath } from '../api/orgs';

export interface FieldDef {
  key: string;
  label: string;
  max: number;
  required: boolean;
  multiline?: boolean;
  email?: boolean;
}

export interface OrgKind {
  path: OrgPath;
  route: 'customers' | 'dealers';
  plural: string;
  singular: string;
  codeKey: 'customerCode' | 'dealerCode';
  codeLabel: string;
  fields: FieldDef[];
  orderFilter: 'customerId' | 'dealerId'; // the query parameter of the admin order list for this kind
}

export const CODE_PATTERN = /^[A-Za-z0-9_-]{2,30}$/; // same rule as the services

const CONTACT: FieldDef[] = [
  { key: 'name', label: 'Name', max: 200, required: true },
  { key: 'contactPerson', label: 'Contact person', max: 150, required: true },
  { key: 'email', label: 'Email', max: 254, required: true, email: true },
  { key: 'phone', label: 'Phone', max: 30, required: true },
];

export const CUSTOMER_KIND: OrgKind = {
  path: '/customers',
  route: 'customers',
  plural: 'Customers',
  singular: 'customer',
  codeKey: 'customerCode',
  codeLabel: 'Customer code',
  orderFilter: 'customerId',
  fields: [
    ...CONTACT,
    { key: 'billingAddress', label: 'Billing address', max: 1000, required: true, multiline: true },
    { key: 'deliveryAddress', label: 'Delivery address', max: 1000, required: true, multiline: true },
  ],
};

export const DEALER_KIND: OrgKind = {
  path: '/dealers',
  route: 'dealers',
  plural: 'Dealers',
  singular: 'dealer',
  codeKey: 'dealerCode',
  codeLabel: 'Dealer code',
  orderFilter: 'dealerId',
  fields: [
    ...CONTACT,
    { key: 'address', label: 'Address', max: 1000, required: true, multiline: true },
    { key: 'taxRegistrationNo', label: 'Tax registration number', max: 50, required: false },
  ],
};
