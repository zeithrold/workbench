<div class='grid gap-1 p-1' role='status' aria-label='Loading options'>
  {#each [0, 1, 2] as row (row)}
    <div class='flex animate-pulse items-center gap-2 rounded-sm px-1 py-2 motion-reduce:animate-none'>
      <span class='size-5 shrink-0 rounded-full bg-muted'></span>
      <span class='grid flex-1 gap-1.5'>
        <span class='h-2.5 rounded bg-muted' style:width={`${68 - row * 9}%`}></span>
        <span class='h-2 rounded bg-muted/70' style:width={`${45 + row * 7}%`}></span>
      </span>
    </div>
  {/each}
</div>
