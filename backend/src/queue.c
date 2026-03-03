#include<stdio.h>
#include<stdlib.h>
#include<string.h>

#define MAX 100


struct Call; 
int high_pr(struct Call a, struct Call b);
void up(int i);
void down(int i);
void insert(struct Call c);
struct Call extract();
struct Call peek();
void display(struct Call c);



struct Call {
    int id, sev, time;  
    char loc[50];
};



struct Call heap[MAX];
int heapSize=0;



int high_pr(struct Call a, struct Call b)
{
    if (a.sev!=b.sev)
    {
        return a.sev>b.sev;  
    }
    else
    {
        return a.time<b.time;  
    }
}



void insert(struct Call c)
{
    if(heapSize>=MAX)
    {
        printf("Heap is full!\n");
        return;
    }
    heap[heapSize]=c;
    int i=heapSize;
    heapSize++;
    up(i);
}



struct Call extract()
{
    struct Call empty={0, 0, 0, ""};
    if (heapSize==0)
    {
        printf("Heap is empty!\n");
        return empty;
    }

    struct Call maxCall=heap[0];
    heap[0]=heap[heapSize - 1];
    heapSize--;

    if(heapSize>0)
    {
        down(0);
    }

    return maxCall;
}



void up(int i)
{
    int p=(i-1)/2;
    while(i>0 && high_pr(heap[i], heap[p]))
    {
        struct Call temp=heap[i];
        heap[i]=heap[p];
        heap[p]=temp;
        i=p;
        p=(i-1)/2;
    }
}



void down(int i)
{
    int lar=i;
    int l=2*i+1;
    int r= 2*i+2;

    if(l<heapSize && high_pr(heap[l], heap[lar]))
    {
        lar=l;
    }
    if(r<heapSize && high_pr(heap[r], heap[lar]))
    {
        lar=r;
    }
    if(lar!=i)
    {
        struct Call temp=heap[i];
        heap[i]=heap[lar];
        heap[lar]=temp;
        down(lar);
    }
}



struct Call peek() 
{
    struct Call empty={0, 0, 0, ""};
    if(heapSize==0)
    {
        return empty;
    }
    return heap[0];
}



void display(struct Call c)
{
    if(c.id==0)
    {
        printf("No call available.\n");
        return;
    }
    printf("ID: %d, Severity: %d, Arrival Time: %d, Location: %s\n",
           c.id, c.sev, c.time, c.loc);
}



int main()
{
    int n;
    printf("Enter the number of calls to insert: ");
    scanf("%d", &n);

    for(int i= 0;i<n;i++)
    {
        struct Call c;
        printf("\nEnter details for call %d:\n", i + 1);
        printf("ID: ");
        scanf("%d", &c.id);
        printf("Severity (1-10): ");
        scanf("%d", &c.sev);
        printf("Arrival time (smaller = earlier) ");
        scanf("%d", &c.time);
        printf("Location: ");
        scanf("%s", c.loc);

        insert(c);
        printf("Call is inserted.\n");
    }

    
    printf("\n-----Top priority call----\n");
    struct Call top=peek();
    display(top);

    
    printf("\n-----Extracting top priority call------\n");
    struct Call extracted=extract();
    display(extracted);

    printf("\n------New top priority call after extraction------\n");
    top=peek();
    display(top);

    return 0;
}
