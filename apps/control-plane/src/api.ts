export type Dashboard = { totalBalanceMinor:number; processedVolumeMinor:number; completedPayments:number; pendingEvents:number; accountCount:number; asOf:string };
export type Account = { id:string; externalRef:string; name:string; currency:string; type:string; status:string; balanceMinor:number; version:number; createdAt:string };
export type Payment = { id:string; merchantReference:string; sourceAccountId:string; destinationAccountId:string; amountMinor:number; currency:string; status:string; createdAt:string; completedAt:string };
export type Posting = { accountId:string; direction:"DEBIT"|"CREDIT"; amountMinor:number; currency:string };
export type LedgerEntry = { id:string; reference:string; type:string; description:string; status:string; occurredAt:string; postings:Posting[] };
export type CreatePayment = { merchantReference:string; sourceAccountId:string; destinationAccountId:string; amountMinor:number; currency:string; description:string };
export interface ClearLedgerClient { dashboard():Promise<Dashboard>; accounts():Promise<Account[]>; payments():Promise<Payment[]>; ledger():Promise<LedgerEntry[]>; createPayment(input:CreatePayment):Promise<Payment> }

const tenant="northstar",baseUrl=import.meta.env.VITE_API_URL??"http://localhost:8080";
class HttpClient implements ClearLedgerClient {
  private async request<T>(path:string,init?:RequestInit):Promise<T>{const response=await fetch(`${baseUrl}${path}`,{...init,headers:{"Content-Type":"application/json","X-Tenant-Id":tenant,...init?.headers}});if(!response.ok){const problem=await response.json().catch(()=>({title:"Request failed"})) as {title?:string;detail?:string};throw new Error(problem.detail??problem.title??`Request failed (${response.status})`)}return response.json() as Promise<T>}
  dashboard(){return this.request<Dashboard>("/api/v1/dashboard")} accounts(){return this.request<Account[]>("/api/v1/accounts")}
  payments(){return this.request<Payment[]>("/api/v1/payments")} ledger(){return this.request<LedgerEntry[]>("/api/v1/ledger/entries")}
  createPayment(input:CreatePayment){return this.request<Payment>("/api/v1/payments",{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:JSON.stringify(input)})}
}
const demoAccounts:Account[]=[
 {id:"11111111-1111-1111-1111-111111111111",externalRef:"WALLET-GBP-OPERATING",name:"Operating wallet",currency:"GBP",type:"WALLET",status:"ACTIVE",balanceMinor:184523900,version:42,createdAt:"2026-03-01T09:00:00Z"},
 {id:"22222222-2222-2222-2222-222222222222",externalRef:"WALLET-GBP-SETTLEMENT",name:"Settlement reserve",currency:"GBP",type:"WALLET",status:"ACTIVE",balanceMinor:48291000,version:18,createdAt:"2026-03-01T09:00:00Z"},
 {id:"33333333-3333-3333-3333-333333333333",externalRef:"MERCHANT-ACME",name:"Acme marketplace",currency:"GBP",type:"MERCHANT",status:"ACTIVE",balanceMinor:8734200,version:11,createdAt:"2026-04-12T09:00:00Z"},
 {id:"44444444-4444-4444-4444-444444444444",externalRef:"MERCHANT-NOVA",name:"Nova stores",currency:"GBP",type:"MERCHANT",status:"ACTIVE",balanceMinor:5267800,version:8,createdAt:"2026-05-22T09:00:00Z"},
];
function p(id:string,ref:string,amount:number,createdAt:string,source:number,destination:number):Payment{return{id:`00000000-0000-0000-0000-00000000${id}`,merchantReference:ref,sourceAccountId:demoAccounts[source].id,destinationAccountId:demoAccounts[destination].id,amountMinor:amount,currency:"GBP",status:"COMPLETED",createdAt,completedAt:createdAt}}
let demoPayments:Payment[]=[p("71a9","ORD-88421",124599,"2026-09-28T17:42:18Z",0,2),p("82bd","ORD-88420",48700,"2026-09-28T17:38:02Z",0,3),p("9cf3","SET-09182",872350,"2026-09-28T17:31:45Z",1,0),p("a14e","ORD-88419",22900,"2026-09-28T17:24:11Z",0,2),p("b25f","ORD-88418",156740,"2026-09-28T17:18:33Z",0,3)];
class DemoClient implements ClearLedgerClient {
 async dashboard(){return{totalBalanceMinor:246816900,processedVolumeMinor:128472900,completedPayments:1842,pendingEvents:0,accountCount:4,asOf:new Date().toISOString()}}
 async accounts(){return demoAccounts} async payments(){return demoPayments}
 async ledger(){return demoPayments.map((payment,index)=>({id:payment.id,reference:`PAY-${payment.id}`,type:"PAYMENT",description:index===2?"Daily settlement":"Merchant order transfer",status:"POSTED",occurredAt:payment.createdAt,postings:[{accountId:payment.sourceAccountId,direction:"DEBIT" as const,amountMinor:payment.amountMinor,currency:payment.currency},{accountId:payment.destinationAccountId,direction:"CREDIT" as const,amountMinor:payment.amountMinor,currency:payment.currency}]}))}
 async createPayment(input:CreatePayment){const created:Payment={...input,id:crypto.randomUUID(),status:"COMPLETED",createdAt:new Date().toISOString(),completedAt:new Date().toISOString()};demoPayments=[created,...demoPayments];return created}
}
export const api:ClearLedgerClient=import.meta.env.VITE_DEMO_MODE==="true"?new DemoClient():new HttpClient();
export const DEMO_MODE=import.meta.env.VITE_DEMO_MODE==="true";
