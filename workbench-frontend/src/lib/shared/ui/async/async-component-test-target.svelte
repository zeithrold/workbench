<script lang='ts'>
  const { message }: { message: string } = $props()
</script>

<div data-testid='async-target'>{message}</div>
